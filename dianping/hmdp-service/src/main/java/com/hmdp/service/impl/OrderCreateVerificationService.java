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
        wrapper.in("task_status", OrderCreateVerifyStatus.PENDING.name(), OrderCreateVerifyStatus.RETRYING.name())
                .le("next_check_time", LocalDateTime.now())
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
        markVerifying(task);

        VoucherOrder order = voucherOrderMapper.selectById(task.getOrderId());
        VoucherReconcileLog deductLog = voucherReconcileLogMapper.selectOne(new QueryWrapper<VoucherReconcileLog>()
                .eq("trace_id", task.getTraceId())
                .eq("log_type", ReconcileLogType.DEDUCT)
                .last("limit 1"));

        if (order != null && deductLog != null) {
            task.setTaskStatus(OrderCreateVerifyStatus.SUCCESS.name());
            task.setLastMessage("order and deduct log verified");
            touch(task);
            taskMapper.updateById(task);
            return;
        }

        int nextRetryCount = task.getRetryCount() + 1;
        task.setRetryCount(nextRetryCount);
        touch(task);

        if (nextRetryCount > task.getMaxRetryCount()) {
            task.setTaskStatus(OrderCreateVerifyStatus.FAILED.name());
            task.setLastMessage("verification exhausted, waiting for recovery or manual review");
            taskMapper.updateById(task);
            if (orderCreateRecoveryService != null) {
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