package com.hmdp.listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.shared.SeckillBugFlags;
import com.hmdp.dto.SeckillVoucherOrderMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.VoucherOrderCreateResult;
import com.hmdp.service.impl.OrderCreateRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀Plus订单创建监听器 — 消费 order-create 消息，异步完成DB建单。
 *
 * <p>在秒杀Plus流程中的位置（第5步）：
 * <pre>{@code
 *   VoucherOrderServiceImpl.seckillVoucherPlus()
 *     → Redis Lua原子扣库存
 *     → RocketMQ order-create 消息
 *     → 【本Listener】消费 → createVoucherOrderPlus()
 * }</pre></p>
 *
 * <p>并发处理设计：
 * <ul>
 *   <li>批量拉取消息（最多32条），通过线程池并发处理</li>
 *   <li>每条消息独立调用 createVoucherOrderPlus()，结果互不影响</li>
 *   <li>OUT_OF_STOCK 时自动触发 Redis 回滚</li>
 *   <li>DUPLICATE 时幂等忽略（消息重复消费场景）</li>
 *   <li>线程池拒绝策略为 CallerRunsPolicy — 防止消息丢失</li>
 * </ul></p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "seckill-plus-order-topic",
        consumerGroup = "seckill-plus-order-consumer-group",
        selectorExpression = "order-create"
)
public class SeckillPlusOrderListener implements RocketMQListener<String>, RocketMQPushConsumerLifecycleListener {
    private static final int MQ_MAX_RECONSUME_TIMES = 3;

    private final IVoucherOrderService voucherOrderService;
    private final OrderCreateRecoveryService orderCreateRecoveryService;

    /** 订单处理线程池 — 核心线程数=CPU核数，最大=2倍CPU核数，有界队列1024，拒绝策略CallerRuns */
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors() * 2,
            60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1024),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /** 单条消息处理入口（Spring RocketMQ 回调） */
    @Override
    public void onMessage(String message) {
        SeckillVoucherOrderMessage orderMessage = JSONUtil.toBean(message, SeckillVoucherOrderMessage.class);
        processMessage(orderMessage, message, 0);
    }

    /**
     * 处理单条订单创建消息。
     *
     * <p>根据 createVoucherOrderPlus 的返回值分4种情况处理：
     * <ol>
     *   <li>CREATED → 订单创建成功</li>
     *   <li>OUT_OF_STOCK → 回滚Redis库存（DB库存不足，恢复Redis供其他用户抢购）</li>
     *   <li>DUPLICATE → 幂等忽略（消息重复消费）</li>
     *   <li>ROLLBACK_FAILED → 仅记日志，留给对账引擎兜底</li>
     * </ol></p>
     *
     * <p>异常兜底：
     * 当 createVoucherOrderPlus 因为DB连接池耗尽、死锁等基础设施问题抛出异常时，
     * 事务已回滚（订单未落库），必须主动回滚 Redis 库存，否则库存永久泄漏。
     * 回滚失败则写 RESTORE 对账日志，供 SeckillReconcileScanJob 兜底。</p>
     */
    void processMessage(org.apache.rocketmq.common.message.MessageExt msg) {
        String body = new String(msg.getBody(), StandardCharsets.UTF_8);
        SeckillVoucherOrderMessage orderMessage = JSONUtil.toBean(body, SeckillVoucherOrderMessage.class);
        processMessage(orderMessage, body, msg.getReconsumeTimes());
    }

    /**
     * 秒杀下单消息核心处理 —— 消费端真正干活的地方。
     *
     * <p>被上层 {@link #processMessage(MessageExt)} 解码后调用，职责是：
     * ① 先跑故障注入埋点（线上才开，压测模拟 JVM 问题）；
     * ② 调 Service 层建订单；
     * ③ 根据建单结果分流处理：库存不足 → 回滚 Redis / 重复消息 → 幂等跳过 / 异常 → 交给 MQ 或恢复任务。</p>
     *
     * <p>异常处理策略：任何异常均被上层 prepareStart 批量监听器捕获，
     * 捕获后写入 recovery_task 表生成恢复任务，由 OrderCreateRecoveryJob 定时兜底补单，
     * Broker 永远收到 CONSUME_SUCCESS（不触发整批重试，避免雪崩）。</p>
     *
     * @param orderMessage   反序列化后的秒杀下单消息（含 orderId/userId/voucherId/traceId）
     * @param sourceMessage  原始 JSON 字符串（写入 recovery_task 表用，保留完整原文方便排查）
     * @param reconsumeTimes 当前消息的 MQ 重试次数（RocketMQ 内部计数器）
     */
    private void processMessage(SeckillVoucherOrderMessage orderMessage, String sourceMessage, int reconsumeTimes) {
        // 把消息里的四个字段拷进 VoucherOrder 实体 —— 后面 Service 调用和日志都用这个对象
        VoucherOrder order = new VoucherOrder()
                .setId(orderMessage.getOrderId())
                .setUserId(orderMessage.getUserId())
                .setVoucherId(orderMessage.getVoucherId())
                .setTraceId(orderMessage.getTraceId());

        // ========== 故障注入埋点（线上通过 SeckillBugConfig 动态开关） ==========
        // 线程本地泄漏：模拟消息解析时把大 buffer 塞进 ThreadLocal 没清 → 堆内存持续膨胀 → OOM
        if (SeckillBugFlags.THREADLOCAL_LEAK.get()) {
            SeckillBugFlags.LEAK_THREADLOCAL.set(new byte[3 * 1024 * 1024]);
        }
        // 线程爆炸：模拟每条消息起独立 newCachedThreadPool + 60s 长任务 → 线程数飙升 → CPU 飙满
        if (SeckillBugFlags.THREAD_EXPLOSION.get()) {
            java.util.concurrent.ExecutorService auditPool =
                    java.util.concurrent.Executors.newCachedThreadPool();
            auditPool.submit(() -> {
                try { Thread.sleep(60_000); } catch (InterruptedException ignored) {}
            });
        }

        // ========== 核心建单：调 Service 层的 createVoucherOrderPlus ==========
        VoucherOrderCreateResult result;
        try {
            result = voucherOrderService.createVoucherOrderPlus(orderMessage);
        } catch (Exception e) {
            // 异常分两种情况处理：
            if (reconsumeTimes < MQ_MAX_RECONSUME_TIMES) {
                // ① 抛出异常由上层 prepareStart 批量监听器捕获，统一写入 recovery_task 表
                log.warn("createVoucherOrderPlus failed, will throw for batch recovery, orderId={}, reconsumeTimes={}",
                        order.getId(), reconsumeTimes, e);
                throw e;
            }
            // ② 重试耗尽 → 直接在本地写 recovery_task 表，让 OrderCreateRecoveryJob 定时扫出来补单
            log.error("createVoucherOrderPlus failed after MQ retries, create recovery task, orderId={}, reconsumeTimes={}",
                    order.getId(), reconsumeTimes, e);
            if (orderCreateRecoveryService != null) {
                orderCreateRecoveryService.createRecoveryTask(orderMessage, sourceMessage, reconsumeTimes);
            }
            return;
        }

        // ========== 建单结果分发 ==========

        // 分支 1：DB 扣库存失败（Redis 够但 DB 不够，极端并发下才会出现的库存不一致）
        // → 必须把 Redis 里扣掉的库存 + 一人一单标记 + DEDUCT 日志回滚掉
        //   否则 Redis 库存会被"幽灵单"永久占着，真实用户买不到
        if (result == VoucherOrderCreateResult.OUT_OF_STOCK) {
            voucherOrderService.rollbackSeckillVoucherPlus(order);
            log.warn("seckill plus order out of stock, rolled back, orderId={}", order.getId());
            return;
        }
        // 分支 2：rollbackSeckillVoucherPlus 自己也失败了（比如 Lua 脚本执行异常）
        // → 彻底失去自动回滚能力，留给 SeckillReconcileScanJob 对账引擎兜底
        //   对账引擎扫 Redis DEDUCT 日志 vs DB 订单表，发现"有扣减日志但没订单"就自动补偿
        if (result == VoucherOrderCreateResult.ROLLBACK_FAILED) {
            log.error("seckill plus rollback failed, left to reconciliation, orderId={}", order.getId());
            return;
        }
        // 分支 3：返回 DUPLICATE —— 说明同一个 orderId 的消息已经被消费过了
        // （可能是 RocketMQ 重投、也可能是上游发了重复消息）
        // 直接幂等忽略，不报错、不重试，保证消费端安全
        if (result == VoucherOrderCreateResult.DUPLICATE) {
            log.info("duplicate seckill plus order message ignored, orderId={}", order.getId());
            return;
        }
        // 正路径：建单成功，打完日志就完了
        log.info("seckill plus order created, orderId={}", order.getId());
    }

    /**
     * 配置批量并发消费 —— RocketMQ 消费者启动扩展钩子。
     * <p>
     * 覆盖默认行为（单条串行消费），改写为：
     * <ol>
     *   <li><b>批量拉取</b>：每次向 Broker 拉取最多 32 条消息，减少网络交互</li>
     *   <li><b>并发处理</b>：32 条消息丢进自建线程池并行执行 {@link #processMessage}()</li>
     *   <li><b>全部完成才 ACK</b>：用 CompletableFuture 等待所有任务结束，
     *       再返回 {@link ConsumeConcurrentlyStatus#CONSUME_SUCCESS} 批量确认，
     *       保证 Broker 认为"已消费"时所有消息真的处理完毕，不会丢</li>
     * </ol>
     * 线程池拒绝策略为 {@link ThreadPoolExecutor.CallerRunsPolicy}，
     * 线程池满时让消费者线程自己跑任务，形成反压，防止秒杀洪峰打爆下游。
     *
     * <p>单条消息处理失败只打日志，不返回 RECONSUME_LATER 让 RocketMQ 重试整批，
     * 而是交给对账引擎（ReconcileScan）兜底补单，避免雪崩式重试。</p>
     *
     * @param consumer RocketMQ Push 消费者实例（连接 Broker 之前调用）
     */
    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        // ① 设置批量拉取上限：默认 1，改成 32，一次和 Broker 交互拿更多消息
        consumer.setConsumeMessageBatchMaxSize(32);

        // ② 替换默认的串行监听器为"并发批量监听器"
        consumer.setMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            List<CompletableFuture<Void>> futures = new ArrayList<>(msgs.size());

            // 逐条包装成异步任务，丢进自建线程池并发执行
            for (MessageExt msg : msgs) {
                futures.add(CompletableFuture.runAsync(() -> processMessage(msg), executor));
            }

            // ③ 阻塞等待所有任务完成 —— 用索引循环，方便异常时拿到对应的原始消息
            for (int i = 0; i < futures.size(); i++) {
                try {
                    futures.get(i).get();
                } catch (Exception e) {
                    // 单条消息处理异常 → 生成恢复任务，让 OrderCreateRecoveryJob 定时兜底补单
                    MessageExt failedMsg = msgs.get(i);
                    String body = new String(failedMsg.getBody(), StandardCharsets.UTF_8);
                    SeckillVoucherOrderMessage orderMessage = JSONUtil.toBean(body, SeckillVoucherOrderMessage.class);
                    log.error("batch process message failed, create recovery task, orderId={}",
                            orderMessage.getOrderId(), e);
                    if (orderCreateRecoveryService != null) {
                        orderCreateRecoveryService.createRecoveryTask(orderMessage, body, failedMsg.getReconsumeTimes());
                    }
                }
            }

            // ④ 全部处理完才告诉 RocketMQ：这批我消费成功了
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
    }
}
