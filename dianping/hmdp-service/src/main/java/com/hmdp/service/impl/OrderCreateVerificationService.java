package com.hmdp.service.impl;

import com.hmdp.dto.SeckillVoucherOrderMessage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.OrderCreateVerifyTask;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.VoucherReconcileLog;
import com.hmdp.mapper.OrderCreateVerifyTaskMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mapper.VoucherReconcileLogMapper;
import com.hmdp.orderverify.model.OrderCreateVerifyStatus;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.shared.reconciliation.model.ReconcileLogType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀订单创建校验服务
 * <p>
 * 秒杀Plus 异步建单链路的对账兜底组件。Redis 扣库存成功后，MQ 消息可能因为各种原因
 * 无法成功建单（MQ 消费失败、DB 宕机、网络抖动等），导致 "Redis 少了库存但 DB 没订单"
 * 的数据不一致。本服务通过定时扫描校验任务，比对订单表和 DEDUCT 对账日志，
 * 检测并修复此类不一致。
 * </p>
 *
 * <p>典型工作流程：
 * <pre>{@code
 *   seckillVoucherPlus 发 MQ 建单消息
 *     ↓
 *   createPendingTask() — 登记校验任务（PENDING）
 *     ↓
 *   OrderCreateVerifyJob（每 5 秒）调用 verifyDueTasks()
 *     ↓
 *   verifySingleTask() — 逐个校验
 *     ├─ DB 有订单 + DB 有 DEDUCT 日志 → SUCCESS（一致）
 *     ├─ 还没建好 → 标记 RETRYING，下次重试
 *     └─ 重试耗尽 → FAILED → 交给 RecoveryService 主动恢复
 * }</pre></p>
 *
 * <p>状态机：
 * <pre>{@code
 *   PENDING → VERIFYING → SUCCESS
 *                      → RETRYING → VERIFYING (循环，直到成功或耗尽)
 *                                → FAILED → RecoveryService → 补偿建单
 * }</pre></p>
 *
 * @see OrderCreateVerifyTask
 * @see OrderCreateRecoveryService
 * @see <a href="package-summary.html">hmdp-job 模块: OrderCreateVerifyJob</a>
 */
@Service
public class OrderCreateVerificationService {

    /** 校验任务表 Mapper */
    private final OrderCreateVerifyTaskMapper taskMapper;
    /** 优惠券订单表 Mapper（校验订单是否真的落库） */
    private final VoucherOrderMapper voucherOrderMapper;
    /** 对账日志表 Mapper（校验 DEDUCT 日志是否存在） */
    private final VoucherReconcileLogMapper voucherReconcileLogMapper;
    /** 优惠券订单服务（可能被恢复流程使用） */
    private final IVoucherOrderService voucherOrderService;
    /** 恢复服务（可选，重试耗尽后转交它主动补建订单） */
    private final OrderCreateRecoveryService orderCreateRecoveryService;
    /** 单个任务最大校验重试次数（默认 5） */
    private final int maxRetryCount;
    /** 每次重试之间的延迟秒数（默认 10） */
    private final int retryDelaySeconds;

    /**
     * Spring 推荐的构造器注入（简化版，恢复服务为 null，使用默认重试参数）
     */
    @Autowired
    public OrderCreateVerificationService(OrderCreateVerifyTaskMapper taskMapper,
                                          VoucherOrderMapper voucherOrderMapper,
                                          VoucherReconcileLogMapper voucherReconcileLogMapper,
                                          IVoucherOrderService voucherOrderService) {
        this(taskMapper, voucherOrderMapper, voucherReconcileLogMapper, voucherOrderService, null, 5, 10);
    }

    /**
     * 全参数构造器（方便单元测试时注入 mock 对象和自定义重试参数）
     *
     * @param taskMapper               校验任务 Mapper
     * @param voucherOrderMapper       订单 Mapper
     * @param voucherReconcileLogMapper 对账日志 Mapper
     * @param voucherOrderService      订单服务
     * @param orderCreateRecoveryService 恢复服务（可为 null，null 时重试耗尽后不触发自动恢复）
     * @param maxRetryCount            最大重试次数
     * @param retryDelaySeconds        重试间隔秒数
     */
    public OrderCreateVerificationService(OrderCreateVerifyTaskMapper taskMapper,
                                          VoucherOrderMapper voucherOrderMapper,
                                          VoucherReconcileLogMapper voucherReconcileLogMapper,
                                          IVoucherOrderService voucherOrderService,
                                          OrderCreateRecoveryService orderCreateRecoveryService,
                                          int maxRetryCount,
                                          int retryDelaySeconds) {
        this.taskMapper = taskMapper;
        this.voucherOrderMapper = voucherOrderMapper;
        this.voucherReconcileLogMapper = voucherReconcileLogMapper;
        this.voucherOrderService = voucherOrderService;
        this.orderCreateRecoveryService = orderCreateRecoveryService;
        this.maxRetryCount = maxRetryCount;
        this.retryDelaySeconds = retryDelaySeconds;
    }

    /**
     * 创建一条待校验的建单追踪任务
     * <p>
     * 在 seckillVoucherPlus 发完 MQ 建单消息后调用。如果订单尚未落库，
     * 就登记一条校验任务，后续由 {@code OrderCreateVerifyJob} 定时巡检确认。
     * </p>
     *
     * @param orderId   订单 ID
     * @param userId    用户 ID
     * @param voucherId 优惠券 ID
     * @param traceId   链路追踪 ID
     */
    public void createPendingTask(Long orderId, Long userId, Long voucherId, String traceId) {
        taskMapper.insert(OrderCreateVerifyTask.pending(
                "verify-order-" + orderId,
                orderId,
                userId,
                voucherId,
                traceId,
                maxRetryCount
        ));
    }

    /**
     * 扫描所有到期的 PENDING / RETRYING 任务并逐个校验
     * <p>
     * 由 {@code OrderCreateVerifyJob} 定时（每 5 秒）调用。每次最多处理 20 条，
     * 避免一次扫描过多任务导致 DB 压力。
     * </p>
     */
    public void verifyDueTasks() {
        QueryWrapper<OrderCreateVerifyTask> wrapper = new QueryWrapper<>();
        // 只扫描"待处理 / 重试中"状态的任务（SUCCESS/FAILED 是终态，不再巡检）
        wrapper.in("task_status", OrderCreateVerifyStatus.PENDING.name(), OrderCreateVerifyStatus.RETRYING.name())
                // 只捞"到期"的任务：next_check_time 由 verifySingleTask 在每次重试后重置，
                // 未到期的任务留给后续 Job 轮次处理，避免一轮 Job 把所有任务都抢光
                .le("next_check_time", LocalDateTime.now())
                // 单次最多处理 20 条 —— Job 每 5 秒跑一轮，小批量 + 高频比大批量 + 低频更平滑，
                // 同时避免一次 selectList 过多行导致 DB 压力尖峰
                .last("limit 20");
        List<OrderCreateVerifyTask> tasks = taskMapper.selectList(wrapper);
        for (OrderCreateVerifyTask task : tasks) {
            verifySingleTask(task);
        }
    }

    /**
     * 校验单个任务 —— 检查 DB 订单和 DEDUCT 对账日志是否同时存在
     *
     * <p>三种可能结果：
     * <ul>
     *   <li><b>订单 + DEDUCT 日志都存在</b> → SUCCESS，建单成功，对账一致</li>
     *   <li><b>还没建出来</b> → RETRYING，下次再查（最多重试 maxRetryCount 次）</li>
     *   <li><b>重试耗尽</b> → FAILED，转交 {@link OrderCreateRecoveryService} 主动恢复</li>
     * </ul></p>
     *
     * @param task 待校验任务
     */
    void verifySingleTask(OrderCreateVerifyTask task) {
        // 抢占标记：先把状态从 PENDING/RETRYING 改成 VERIFYING 再做校验。
        // 因为 verifyDueTasks 的扫描条件只捞 PENDING/RETRYING（见 .in("task_status", ...)），
        // 改成 VERIFYING 后即使有多个 Job 实例跑，这条任务也不会被重复捞到 —— 这是一种轻量级的
        // "无锁抢占"设计，比 Redisson 分布式锁代价小得多，单机/多节点部署都适用
        markVerifying(task);

        // 同时查订单表 + DEDUCT 对账日志 —— 只有两者都存在才算"建单成功且 DB 库存扣了"。
        // 只查 order 不够：如果消费者 insert 成功但 DEDUCT 日志没写（事务异常/代码漏写），
        // 就会出现"订单在但库存没扣"的不一致，这正是对账机制要兜底的
        VoucherOrder order = voucherOrderMapper.selectById(task.getOrderId());
        VoucherReconcileLog deductLog = voucherReconcileLogMapper.selectOne(new QueryWrapper<VoucherReconcileLog>()
                .eq("trace_id", task.getTraceId())
                .eq("log_type", ReconcileLogType.DEDUCT)
                .last("limit 1"));

        if (order != null && deductLog != null) {
            // ✅ 成功闭环：订单 + DEDUCT 日志都落库了，状态转 SUCCESS，updateById 后不再被 Job 扫描
            task.setTaskStatus(OrderCreateVerifyStatus.SUCCESS.name());
            task.setLastMessage("order and deduct log verified");
            touch(task);
            taskMapper.updateById(task);
            return;
        }

        // ❌ 还没齐：要么 MQ 消费者还没建完单，要么建单了但 DEDUCT 日志还没落（事务还在跑）
        int nextRetryCount = task.getRetryCount() + 1;
        task.setRetryCount(nextRetryCount);
        touch(task);

        if (nextRetryCount > task.getMaxRetryCount()) {
            // 🔴 重试耗尽：转 FAILED 终态，同时给 OrderCreateRecoveryService 建一条恢复任务。
            // 注意两个服务是接力关系：Verification 只负责"观察"，Recovery 负责"主动介入建单"，
            // 这样两个职责解耦，Verification 不用关心 Recovery 的内部细节
            task.setTaskStatus(OrderCreateVerifyStatus.FAILED.name());
            task.setLastMessage("verification exhausted, waiting for recovery or manual review");
            taskMapper.updateById(task);
            if (orderCreateRecoveryService != null) {
                // null 检查说明 RecoveryService 是可选注入（可能因循环依赖或某些部署不开启），
                // 这种情况下 FAILED 就真的留给人工处理了
                SeckillVoucherOrderMessage message = new SeckillVoucherOrderMessage();
                message.setOrderId(task.getOrderId());
                message.setUserId(task.getUserId());
                message.setVoucherId(task.getVoucherId());
                message.setTraceId(task.getTraceId());
                orderCreateRecoveryService.createRecoveryTask(
                        message,
                        cn.hutool.json.JSONUtil.toJsonStr(message),
                        nextRetryCount
                );
            }
            return;
        }

        // 🟡 继续等：状态转 RETRYING，next_check_time 重置为 retryDelaySeconds 后，
        // 下次 Job 扫描时 .le("next_check_time", now) 才能再捞到 —— 配合 verifyDueTasks 的 limit 20
        // 形成自然的"退避节奏"：没到期的任务自动让给到期的先处理
        task.setTaskStatus(OrderCreateVerifyStatus.RETRYING.name());
        task.setLastMessage("order or deduct log not ready, wait next verification");
        task.setNextCheckTime(LocalDateTime.now().plusSeconds(retryDelaySeconds));
        taskMapper.updateById(task);
    }

    /**
     * 标记任务状态为 VERIFYING（正在校验）
     */
    private void markVerifying(OrderCreateVerifyTask task) {
        task.setTaskStatus(OrderCreateVerifyStatus.VERIFYING.name());
        task.setLastMessage("verifying order create result");
        touch(task);
        taskMapper.updateById(task);
    }

    /**
     * 更新 updatedAt / updateTime 字段为当前时间
     */
    private void touch(OrderCreateVerifyTask task) {
        LocalDateTime now = LocalDateTime.now();
        task.setUpdatedAt(now);
        task.setUpdateTime(now);
    }
}