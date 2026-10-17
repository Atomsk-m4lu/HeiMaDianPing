package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.entity.SeckillReconcileTask;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.VoucherReconcileLog;
import com.hmdp.mapper.SeckillReconcileTaskMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mapper.VoucherReconcileLogMapper;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.shared.reconciliation.model.ReconcileLogType;
import com.hmdp.shared.reconciliation.model.SeckillReconcileAnomalyType;
import com.hmdp.shared.reconciliation.model.SeckillReconcileCompensationStatus;
import com.hmdp.shared.reconciliation.model.SeckillReconcileTaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// TODO 这段代码废弃掉
@Slf4j
@Service
public class SeckillReconcileCompensationService {

    /** 卡在 PROCESSING 超过该分钟数视为崩溃残留（补偿有副作用，阈值必须远大于正常耗时） */
    private static final int STUCK_PROCESSING_MINUTES = 5;

    private final SeckillReconcileTaskMapper taskMapper;
    private final VoucherOrderMapper voucherOrderMapper;
    private final VoucherReconcileLogMapper voucherReconcileLogMapper;
    private final IVoucherOrderService voucherOrderService;
    private final boolean autoCompensationEnabled;
    private final int batchSize;
    private final int retryDelaySeconds;

    @Autowired
    public SeckillReconcileCompensationService(SeckillReconcileTaskMapper taskMapper,
                                               VoucherOrderMapper voucherOrderMapper,
                                               VoucherReconcileLogMapper voucherReconcileLogMapper,
                                               IVoucherOrderService voucherOrderService,
                                               @Value("${seckill.reconcile.auto-compensation-enabled:false}") boolean autoCompensationEnabled,
                                               @Value("${seckill.reconcile.scan-batch-size:100}") int batchSize,
                                               @Value("${seckill.reconcile.compensation-retry-delay-seconds:30}") int retryDelaySeconds) {
        this.taskMapper = taskMapper;
        this.voucherOrderMapper = voucherOrderMapper;
        this.voucherReconcileLogMapper = voucherReconcileLogMapper;
        this.voucherOrderService = voucherOrderService;
        this.autoCompensationEnabled = autoCompensationEnabled;
        this.batchSize = batchSize;
        this.retryDelaySeconds = retryDelaySeconds;
    }

    /**
     * 定时扫描 WAITING 状态的对账异常 task 并执行自动补偿。
     *
     * <p>和 Verification / Recovery 的区别：这是对账链路的"兜底闭环"——前面两层发现异常后，
     * 只有 autoCompensationEnabled=true 且异常类型为 DEDUCT_PRESENT_ORDER_MISSING（库存真扣了但订单丢了）
     * 的 task 才会走到这里；其他异常一律等人工。</p>
     *
     * <p>三层扫描条件的配合拳：
     * <ul>
     *   <li>compensation_status = WAITING —— 只捞"等补偿"的 task，已处理（DONE/NOT_NEEDED）或失败（FAILED）的跳过</li>
     *   <li>next_execute_time <= now —— 调度节流，和 processSingleTask 里 markFailed 重置 nextExecuteTime 配对，形成退避节奏</li>
     *   <li>limit batchSize —— 同 Recovery，单条补偿要调 rollbackSeckillVoucherPlus（涉及 Redis），重操作必须限量</li>
     * </ul></p>
     */
    public void processDueTasks() {
        // 总闸门：autoCompensationEnabled=false 时整个补偿链路不跑 —— 生产环境建议默认关，
        // 只有对账系统稳定验证过"DEDUCT_PRESENT_ORDER_MISSING 自动补偿逻辑没问题"后再打开
        if (!autoCompensationEnabled) {
            return;
        }
        // 先回收崩溃残留：CAS 抢占只认 PENDING，PROCESSING 残留在不回收的情况下永远无人处理
        resetStuckProcessingTasks(STUCK_PROCESSING_MINUTES);
        QueryWrapper<SeckillReconcileTask> wrapper = new QueryWrapper<>();
        // 四条件联合筛选：只捞"等补偿 + 未抢占 + 已到期 + 限量"的 task
        wrapper.eq("compensation_status", SeckillReconcileCompensationStatus.WAITING.name())
                .eq("task_status", SeckillReconcileTaskStatus.PENDING.name())
                .le("next_execute_time", LocalDateTime.now())
                .last("limit " + batchSize);
        List<SeckillReconcileTask> tasks = taskMapper.selectList(wrapper);
        for (SeckillReconcileTask task : tasks) {
            processSingleTask(task);
        }
    }

    /**
     * 执行单条对账异常的自动补偿 —— 对 DEDUCT_PRESENT_ORDER_MISSING 场景（库存真扣了但订单丢了）
     * 回滚 Redis 库存，让这笔优惠券能被其他用户重新抢到。
     *
     * <p>⚠️ 这是一个<b>有副作用的破坏性操作</b>（回滚库存），所以比 Verification/Recovery 的处理多了
     * 两道预检查防线 —— 确保"现在库存真的需要还回去"才动手，避免误补偿导致超卖。</p>
     *
     * <p>执行流程：</p>
     * <pre>
     *   markProcessing 抢占 → anomalyType 校验 → 订单存在性检查 → 日志状态检查 → 回滚库存 → 双状态闭环
     *     (无锁)          (类型门槛)        (自愈了就跳过)       (兜底防线)      (核心动作)    (task + comp)
     * </pre>
     */
    void processSingleTask(SeckillReconcileTask task) {
        // CAS 抢占：只有把 task_status 从 PENDING 原子改成 PROCESSING 的线程才能继续。
        // 补偿是回滚库存的破坏性动作，抢占失败（已被其他实例/上一轮处理）必须直接跳过，避免重复回补。
        if (!markProcessing(task)) {
            return;
        }

        // 🔒 第一道门槛：再校一次 anomalyType —— 理论上 createTaskIfAbsent 已经过滤过，
        // 这里是"执行前最后一道保险"，防止代码改动或 task 被外部篡改后误补偿其他类型异常
        if (!SeckillReconcileAnomalyType.DEDUCT_PRESENT_ORDER_MISSING.name().equals(task.getAnomalyType())) {
            markFailed(task, "unsupported anomaly type for auto compensation");
            return;
        }

        // 🛡️ 第二道防线：订单是否已经"自愈"？
        // 扫描当时发现订单不存在，但可能 Recovery 后来成功建单了（或者消费者第一次就没成功、第二次重试成功了），
        // 这时库存和订单都已经一致，不需要回滚了 —— 这就是"先扫描后补偿"模型的经典竞态：
        // 扫描 → 发现异常 → 建 task → 补偿前，异常可能已经被其他链路修好了
        VoucherOrder order = voucherOrderMapper.selectById(task.getOrderId());
        if (order != null) {
            resolveWithoutRollback(task, "order already exists, no compensation needed");
            return;
        }

        // 🛡️ 第三道防线：日志状态双校验
        // DEDUCT 日志不存在？说明库存根本没扣（扫描时可能是误判，或者 DEDUCT 日志也被清理了）
        // RESTORE 日志存在？说明库存已经被回滚过了（可能是手动、可能是之前补偿成功）
        // 任一命中都意味着"不需要再补"，直接闭环
        VoucherReconcileLog deductLog = voucherReconcileLogMapper.selectOne(new QueryWrapper<VoucherReconcileLog>()
                .eq("trace_id", task.getTraceId())
                .eq("log_type", ReconcileLogType.DEDUCT)
                .last("limit 1"));
        VoucherReconcileLog restoreLog = voucherReconcileLogMapper.selectOne(new QueryWrapper<VoucherReconcileLog>()
                .eq("trace_id", task.getTraceId())
                .eq("log_type", ReconcileLogType.RESTORE)
                .last("limit 1"));
        if (deductLog == null || restoreLog != null) {
            resolveWithoutRollback(task, "compensation pre-check no longer matches");
            return;
        }

        // ✅ 三道防线都过了 —— 执行核心补偿动作：回滚 Redis 库存
        // 注意：rollbackSeckillVoucherPlus 内部会同时写一条 RESTORE 类型的对账日志，
        // 所以上面的第三道防线查 RESTORE 日志就是为了"补偿后能被识别"
        // 这里 new 一个 VoucherOrder 只是为了凑齐 rollback 方法需要的参数（从 task 字段拼）
        voucherOrderService.rollbackSeckillVoucherPlus(new VoucherOrder()
                .setId(task.getOrderId())
                .setUserId(task.getUserId())
                .setVoucherId(task.getVoucherId())
                .setTraceId(task.getTraceId()));

        // 双状态闭环：
        // taskStatus=RESOLVED（任务本身解决）+ compensationStatus=DONE（补偿动作完成）
        // 两个维度独立：compensationStatus 还有 NOT_NEEDED / FAILED 等取值，
        // 所以必须同时 set，不能复用 taskStatus 表达补偿结果
        task.setCompensationStatus(SeckillReconcileCompensationStatus.DONE.name());
        task.setTaskStatus(SeckillReconcileTaskStatus.RESOLVED.name());
        task.setAnomalyDetail("rollback finished for missing order");
        touch(task);
        taskMapper.updateById(task);
    }

    /**
     * CAS 抢占 —— 只有把 task_status 从 PENDING 原子地改成 PROCESSING 的线程才能执行补偿。
     * <p>返回 false 表示已被其他实例/上一轮抢占（或状态已变），本线程直接跳过；
     * 这是补偿动作（回滚库存）防重复执行的关键闸门。</p>
     *
     * @return true=抢占成功，可以执行补偿；false=未抢到，直接跳过
     */
    private boolean markProcessing(SeckillReconcileTask task) {
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<SeckillReconcileTask> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", task.getId())
                .eq("task_status", SeckillReconcileTaskStatus.PENDING.name())
                .eq("compensation_status", SeckillReconcileCompensationStatus.WAITING.name())
                .set("task_status", SeckillReconcileTaskStatus.PROCESSING.name())
                .set("last_execute_time", now)
                .set("updated_at", now)
                .set("update_time", now);
        int claimed = taskMapper.update(null, wrapper);
        if (claimed == 0) {
            return false;
        }
        task.setTaskStatus(SeckillReconcileTaskStatus.PROCESSING.name());
        task.setLastExecuteTime(now);
        task.setUpdatedAt(now);
        task.setUpdateTime(now);
        return true;
    }

    /**
     * 回收卡在 PROCESSING 的补偿任务 —— 进程崩溃/退出会留下 PROCESSING 残留，
     * 而 CAS 抢占只认 PENDING，不回收就永远没人能再处理。按超时重置回 PENDING 后由下一轮 Job 重新抢占。
     * <p>补偿是有副作用的动作（回滚库存），所以回收阈值远大于正常执行耗时（默认 5 分钟），
     * 且执行前仍保留订单存在性、DEDUCT/RESTORE 日志三道预检查，重复执行会被前置校验拦下。</p>
     *
     * @param staleMinutes 超过多少分钟未更新的 PROCESSING 任务视为崩溃残留
     * @return 被重置的任务数
     */
    public int resetStuckProcessingTasks(int staleMinutes) {
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<SeckillReconcileTask> wrapper = new UpdateWrapper<>();
        wrapper.eq("task_status", SeckillReconcileTaskStatus.PROCESSING.name())
                .eq("compensation_status", SeckillReconcileCompensationStatus.WAITING.name())
                .lt("update_time", now.minusMinutes(staleMinutes))
                .set("task_status", SeckillReconcileTaskStatus.PENDING.name())
                .set("next_execute_time", now)
                .set("updated_at", now)
                .set("update_time", now);
        int reset = taskMapper.update(null, wrapper);
        if (reset > 0) {
            log.warn("reset {} stuck PROCESSING compensation tasks (> {}min)", reset, staleMinutes);
        }
        return reset;
    }

    private void resolveWithoutRollback(SeckillReconcileTask task, String detail) {
        task.setTaskStatus(SeckillReconcileTaskStatus.RESOLVED.name());
        task.setAnomalyDetail(detail);
        if (!SeckillReconcileCompensationStatus.DONE.name().equals(task.getCompensationStatus())) {
            task.setCompensationStatus(SeckillReconcileCompensationStatus.NOT_NEEDED.name());
        }
        touch(task);
        taskMapper.updateById(task);
    }

    private void markFailed(SeckillReconcileTask task, String detail) {
        task.setRetryCount(task.getRetryCount() + 1);
        task.setTaskStatus(SeckillReconcileTaskStatus.FAILED.name());
        task.setCompensationStatus(SeckillReconcileCompensationStatus.FAILED.name());
        task.setAnomalyDetail(detail);
        task.setNextExecuteTime(LocalDateTime.now().plusSeconds(retryDelaySeconds));
        touch(task);
        taskMapper.updateById(task);
    }

    private void touch(SeckillReconcileTask task) {
        LocalDateTime now = LocalDateTime.now();
        task.setUpdatedAt(now);
        task.setUpdateTime(now);
    }
}