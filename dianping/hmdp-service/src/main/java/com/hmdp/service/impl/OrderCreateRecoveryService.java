package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.SeckillVoucherOrderMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.OrderCreateRecoveryTask;
import com.hmdp.mapper.OrderCreateRecoveryTaskMapper;
import com.hmdp.orderverify.model.OrderCreateRecoveryStatus;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.VoucherOrderCreateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀订单创建恢复服务 —— 兜底 MQ 重试耗尽后仍未建单成功的秒杀请求。
 *
 * <p>任务状态流转（{@link OrderCreateRecoveryStatus}）：
 * <pre>{@code
 *   PENDING ──→ PROCESSING ──→ SUCCESS（建单成功或已存在，幂等）
 *                    │
 *                    ├── 业务终态（如库存不足，非基础设施故障）──→ MANUAL_REVIEW
 *                    │
 *                    └── 基础设施异常（DB/Redis/MQ 挂了）
 *                           ├── retryCount 未超限 ──→ RETRYING ──(delay)──→ 再次 PROCESSING
 *                           └── retryCount 超限 ──→ MANUAL_REVIEW
 *
 *   MANUAL_REVIEW ──→ SUCCESS（人工确认已建单）或 CLOSED（人工放弃并回滚库存）
 * }</pre></p>
 *
 * <p>配置项（全部有默认值，可通过 application.yml 覆盖）：
 * <ul>
 *   <li>{@code seckill.order-create-recovery.max-retry-count=5} — 自动重试上限</li>
 *   <li>{@code seckill.order-create-recovery.retry-delay-seconds=10} — 重试间隔秒数</li>
 *   <li>{@code seckill.order-create-recovery.manual-review-window-minutes=5} — 人工审核超时窗口</li>
 * </ul></p>
 */
@Service
public class OrderCreateRecoveryService {

    /** 恢复任务表 Mapper —— 持久化任务状态、扫描到期任务 */
    private final OrderCreateRecoveryTaskMapper taskMapper;

    /** 订单创建服务 —— 恢复时复用秒杀建单逻辑（依赖其幂等性） */
    private final IVoucherOrderService voucherOrderService;

    /** 自动重试次数上限，超过后升级人工审核 */
    private final int maxRetryCount;

    /** 两次自动重试之间的间隔秒数 */
    private final int retryDelaySeconds;

    /** 人工审核超时窗口（分钟），超期未处理需告警升级 */
    private final int manualReviewWindowMinutes;

    /**
     * 构造器注入 —— 所有配置项通过 @Value 注入，均有默认值兜底。
     *
     * @param taskMapper                  恢复任务表 Mapper
     * @param voucherOrderService         订单创建服务
     * @param maxRetryCount               自动重试上限（默认 5）
     * @param retryDelaySeconds           重试间隔秒数（默认 10）
     * @param manualReviewWindowMinutes   人工审核超时窗口分钟（默认 5）
     */
    @Autowired
    public OrderCreateRecoveryService(OrderCreateRecoveryTaskMapper taskMapper,
                                      IVoucherOrderService voucherOrderService,
                                      @Value("${seckill.order-create-recovery.max-retry-count:5}") int maxRetryCount,
                                      @Value("${seckill.order-create-recovery.retry-delay-seconds:10}") int retryDelaySeconds,
                                      @Value("${seckill.order-create-recovery.manual-review-window-minutes:5}") int manualReviewWindowMinutes) {
        this.taskMapper = taskMapper;
        this.voucherOrderService = voucherOrderService;
        this.maxRetryCount = maxRetryCount;
        this.retryDelaySeconds = retryDelaySeconds;
        this.manualReviewWindowMinutes = manualReviewWindowMinutes;
    }

    /**
     * 创建一条恢复任务 —— 由 MQ Listener 在重试耗尽后调用。
     * <p>
     * 把原始秒杀消息（JSON 字符串）、订单信息、TraceId 等持久化到
     * {@code order_create_recovery_task} 表，初始状态 PENDING，retryCount=0。
     * </p>
     *
     * @param orderMessage   原始秒杀下单消息
     * @param sourceMessage  消息的 JSON 原文（用于后续恢复时反序列化）
     * @param reconsumeTimes RocketMQ 实际重试次数（用于日志追踪）
     */
    public void createRecoveryTask(SeckillVoucherOrderMessage orderMessage, String sourceMessage, int reconsumeTimes) {
        taskMapper.insert(OrderCreateRecoveryTask.pending(
                "recover-order-" + orderMessage.getOrderId(),
                orderMessage.getOrderId(),
                orderMessage.getUserId(),
                orderMessage.getVoucherId(),
                orderMessage.getTraceId(),
                sourceMessage,
                maxRetryCount
        ).setLastMessage("MQ retries exhausted at reconsumeTimes=" + reconsumeTimes));
    }

    /**
     * 扫描到期任务并逐条恢复 —— 由上层 Job（{@code hmdp-job} 模块的 OrderCreateRecoveryJob）定时调用。
     * <p>
     * 查询条件：task_status IN (PENDING, RETRYING) AND next_execute_time <= now，limit 20。
     * 注意此处没有外层 try-catch，因为单条任务的异常处理下沉到了 {@link #processSingleTask} 内部。
     * </p>
     */
    public void processDueTasks() {
        QueryWrapper<OrderCreateRecoveryTask> wrapper = new QueryWrapper<>();
        wrapper.in("task_status", OrderCreateRecoveryStatus.PENDING.name(), OrderCreateRecoveryStatus.RETRYING.name())
                .le("next_execute_time", LocalDateTime.now())
                .last("limit 20");
        List<OrderCreateRecoveryTask> tasks = taskMapper.selectList(wrapper);
        for (OrderCreateRecoveryTask task : tasks) {
            processSingleTask(task);
        }
    }

    /**
     * 人工审核：放弃订单并回滚库存预占。
     * <p>
     * 场景：运营 / 开发人员确认该秒杀请求应作废（如用户已主动取消），
     * 调用此方法释放优惠券库存并关闭恢复任务。
     * </p>
     *
     * @param task 要放弃的恢复任务
     */
    public void abandonAndRollback(OrderCreateRecoveryTask task) {
        voucherOrderService.rollbackSeckillVoucherPlus(new VoucherOrder()
                .setId(task.getOrderId())
                .setUserId(task.getUserId())
                .setVoucherId(task.getVoucherId())
                .setTraceId(task.getTraceId()));
        task.setTaskStatus(OrderCreateRecoveryStatus.CLOSED.name());
        task.setLastMessage("manual review abandoned order and rolled back reservation");
        touch(task);
        taskMapper.updateById(task);
    }

    /**
     * 人工审核：标记该恢复任务已成功（如运营确认订单已在别处创建）。
     *
     * @param task    要标记的恢复任务
     * @param message 人工备注，记录确认依据
     */
    public void markManualSuccess(OrderCreateRecoveryTask task, String message) {
        task.setTaskStatus(OrderCreateRecoveryStatus.SUCCESS.name());
        task.setLastMessage(message);
        touch(task);
        taskMapper.updateById(task);
    }

    /**
     * 处理单条恢复任务 —— 核心状态机逻辑。
     * <p>
     * 执行流程：
     * <pre>{@code
     * ① 标记为 PROCESSING（防止并发重复处理）
     * ② 反序列化原始秒杀消息
     * ③ 调 voucherOrderService.createVoucherOrderPlus() 重建订单
     *    ├── CREATED / DUPLICATE → SUCCESS（幂等，已建过也算成功）
     *    ├── 业务终态（如 NO_STOCK）→ MANUAL_REVIEW（非基础设施故障，重试没用）
     *    └── 抛异常（基础设施故障）
     *         ├── retryCount 未超限 → RETRYING + 推迟 nextExecuteTime
     *         └── retryCount 超限 → MANUAL_REVIEW
     * }</pre></p>
     */
    void processSingleTask(OrderCreateRecoveryTask task) {
        markProcessing(task);
        SeckillVoucherOrderMessage message = JSONUtil.toBean(task.getSourceMessage(), SeckillVoucherOrderMessage.class);
        try {
            VoucherOrderCreateResult result = voucherOrderService.createVoucherOrderPlus(message);
            if (result == VoucherOrderCreateResult.CREATED || result == VoucherOrderCreateResult.DUPLICATE) {
                // 建单成功 或 已存在（幂等命中）→ 任务成功结束
                task.setTaskStatus(OrderCreateRecoveryStatus.SUCCESS.name());
                task.setLastMessage("recovery create order succeeded");
                touch(task);
                taskMapper.updateById(task);
                return;
            }
            // 业务终态结果（如库存不足、活动已结束等）—— 非基础设施故障，自动重试没用
            moveToManualReview(task, "recovery received terminal business result: " + result.name());
        } catch (Exception e) {
            // 基础设施异常（DB/Redis/MQ 挂了）—— 可以自动重试
            int nextRetryCount = task.getRetryCount() + 1;
            task.setRetryCount(nextRetryCount);
            if (nextRetryCount > task.getMaxRetryCount()) {
                // 自动重试也耗尽了 → 升级人工
                moveToManualReview(task, "recovery exhausted after infrastructure failure");
                return;
            }
            // 还能重试 → 推迟到 retryDelaySeconds 之后
            task.setTaskStatus(OrderCreateRecoveryStatus.RETRYING.name());
            task.setLastMessage("recovery failed, waiting next retry");
            task.setNextExecuteTime(LocalDateTime.now().plusSeconds(retryDelaySeconds));
            touch(task);
            taskMapper.updateById(task);
        }
    }

    /**
     * 标记任务为 PROCESSING —— 进入实际建单前的前置状态，防止并发重复处理。
     */
    private void markProcessing(OrderCreateRecoveryTask task) {
        task.setTaskStatus(OrderCreateRecoveryStatus.PROCESSING.name());
        task.setLastMessage("processing recovery order create task");
        touch(task);
        taskMapper.updateById(task);
    }

    /**
     * 升级为人工审核 —— 自动恢复已无意义，需要人工介入。
     * <p>
     * 同时设置 alarmStatus=PENDING（待告警）和 manualDeadline（超时截止时间），
     * 供外部告警系统扫描并通知运维 / 运营人员处理。
     * </p>
     */
    private void moveToManualReview(OrderCreateRecoveryTask task, String message) {
        task.setTaskStatus(OrderCreateRecoveryStatus.MANUAL_REVIEW.name());
        task.setLastMessage(message);
        task.setAlarmStatus("PENDING");
        task.setManualDeadline(LocalDateTime.now().plusMinutes(manualReviewWindowMinutes));
        touch(task);
        taskMapper.updateById(task);
    }

    /**
     * 统一更新时间戳 —— 每次状态变更都应调用，保持 updatedAt 和 updateTime 同步为当前时间。
     */
    private void touch(OrderCreateRecoveryTask task) {
        LocalDateTime now = LocalDateTime.now();
        task.setUpdatedAt(now);
        task.setUpdateTime(now);
    }
}