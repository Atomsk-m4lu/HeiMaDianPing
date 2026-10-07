package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// TODO 这段代码废弃掉
@Service
public class SeckillReconcileCompensationService {

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

    public void processDueTasks() {
        if (!autoCompensationEnabled) {
            return;
        }
        QueryWrapper<SeckillReconcileTask> wrapper = new QueryWrapper<>();
        wrapper.eq("compensation_status", SeckillReconcileCompensationStatus.WAITING.name())
                .le("next_execute_time", LocalDateTime.now())
                .last("limit " + batchSize);
        List<SeckillReconcileTask> tasks = taskMapper.selectList(wrapper);
        for (SeckillReconcileTask task : tasks) {
            processSingleTask(task);
        }
    }

    void processSingleTask(SeckillReconcileTask task) {
        markProcessing(task);
        if (!SeckillReconcileAnomalyType.DEDUCT_PRESENT_ORDER_MISSING.name().equals(task.getAnomalyType())) {
            markFailed(task, "unsupported anomaly type for auto compensation");
            return;
        }

        VoucherOrder order = voucherOrderMapper.selectById(task.getOrderId());
        if (order != null) {
            resolveWithoutRollback(task, "order already exists, no compensation needed");
            return;
        }

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

        voucherOrderService.rollbackSeckillVoucherPlus(new VoucherOrder()
                .setId(task.getOrderId())
                .setUserId(task.getUserId())
                .setVoucherId(task.getVoucherId())
                .setTraceId(task.getTraceId()));
        task.setCompensationStatus(SeckillReconcileCompensationStatus.DONE.name());
        task.setTaskStatus(SeckillReconcileTaskStatus.RESOLVED.name());
        task.setAnomalyDetail("rollback finished for missing order");
        touch(task);
        taskMapper.updateById(task);
    }

    private void markProcessing(SeckillReconcileTask task) {
        task.setTaskStatus(SeckillReconcileTaskStatus.PROCESSING.name());
        task.setLastExecuteTime(LocalDateTime.now());
        touch(task);
        taskMapper.updateById(task);
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
