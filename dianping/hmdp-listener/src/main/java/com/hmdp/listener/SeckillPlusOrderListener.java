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

    private void processMessage(SeckillVoucherOrderMessage orderMessage, String sourceMessage, int reconsumeTimes) {
        VoucherOrder order = new VoucherOrder()
                .setId(orderMessage.getOrderId())
                .setUserId(orderMessage.getUserId())
                .setVoucherId(orderMessage.getVoucherId())
                .setTraceId(orderMessage.getTraceId());

        // ========== 埋点：JVM问题注入（由SeckillBugConfig开关控制） ==========
        if (SeckillBugFlags.THREADLOCAL_LEAK.get()) {
            // 模拟"消息解析buffer"，分配后未清理
            SeckillBugFlags.LEAK_THREADLOCAL.set(new byte[3 * 1024 * 1024]); // 3MB
        }
        if (SeckillBugFlags.THREAD_EXPLOSION.get()) {
            // 模拟"旁路审计日志"，每条消息起独立线程
            java.util.concurrent.ExecutorService auditPool =
                    java.util.concurrent.Executors.newCachedThreadPool();
            auditPool.submit(() -> {
                try { Thread.sleep(60_000); } catch (InterruptedException ignored) {}
            });
        }

        VoucherOrderCreateResult result;
        try {
            result = voucherOrderService.createVoucherOrderPlus(orderMessage);
        } catch (Exception e) {
            if (reconsumeTimes < MQ_MAX_RECONSUME_TIMES) {
                log.warn("createVoucherOrderPlus failed, request MQ retry, orderId={}, reconsumeTimes={}",
                        order.getId(), reconsumeTimes, e);
                throw e;
            }
            log.error("createVoucherOrderPlus failed after MQ retries, create recovery task, orderId={}, reconsumeTimes={}",
                    order.getId(), reconsumeTimes, e);
            if (orderCreateRecoveryService != null) {
                orderCreateRecoveryService.createRecoveryTask(orderMessage, sourceMessage, reconsumeTimes);
            }
            return;
        }

        if (result == VoucherOrderCreateResult.OUT_OF_STOCK) {
            // DB库存不足 → 回滚Redis库存，把机会让给其他用户
            voucherOrderService.rollbackSeckillVoucherPlus(order);
            log.warn("seckill plus order out of stock, rolled back, orderId={}", order.getId());
            return;
        }
        if (result == VoucherOrderCreateResult.ROLLBACK_FAILED) {
            // 回滚失败 → 对账引擎兜底
            log.error("seckill plus rollback failed, left to reconciliation, orderId={}", order.getId());
            return;
        }
        if (result == VoucherOrderCreateResult.DUPLICATE) {
            // 重复消息，幂等忽略
            log.info("duplicate seckill plus order message ignored, orderId={}", order.getId());
            return;
        }
        log.info("seckill plus order created, orderId={}", order.getId());
    }

    /**
     * 配置批量消费 — 覆盖默认的单条消费行为。
     *
     * <p>每次拉取最多32条消息，通过线程池并发处理，
     * 使用 CompletableFuture.allOf 等待所有任务完成后才返回 CONSUME_SUCCESS，
     * 保证 batch ACK 前所有消息都已处理完成。</p>
     */
    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        consumer.setConsumeMessageBatchMaxSize(32);
        consumer.setMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (MessageExt msg : msgs) {
                String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                futures.add(CompletableFuture.runAsync(() -> processMessage(msg), executor));
            }
            for (CompletableFuture<Void> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    log.error("batch process message unexpected error, left to reconciliation", e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
    }
}
