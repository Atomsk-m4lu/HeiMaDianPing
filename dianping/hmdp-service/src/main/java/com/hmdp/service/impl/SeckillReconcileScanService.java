package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.SeckillReconcileTask;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.VoucherReconcileLog;
import com.hmdp.mapper.SeckillReconcileTaskMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mapper.VoucherReconcileLogMapper;
import com.hmdp.shared.reconciliation.model.ReconcileLogType;
import com.hmdp.shared.reconciliation.model.SeckillReconcileAnomalyType;
import com.hmdp.shared.reconciliation.model.SeckillReconcileCompensationStatus;
import com.hmdp.shared.reconciliation.model.SeckillReconcileTaskStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeckillReconcileScanService {

    private final SeckillReconcileTaskMapper taskMapper;
    private final VoucherOrderMapper voucherOrderMapper;
    private final VoucherReconcileLogMapper voucherReconcileLogMapper;
    private final int scanBatchSize;
    private final int scanDelayMinutes;
    private final int compensationMaxRetryCount;
    private final boolean autoCompensationEnabled;

    @Autowired
    public SeckillReconcileScanService(SeckillReconcileTaskMapper taskMapper,
                                       VoucherOrderMapper voucherOrderMapper,
                                       VoucherReconcileLogMapper voucherReconcileLogMapper,
                                       @Value("${seckill.reconcile.scan-batch-size:100}") int scanBatchSize,
                                       @Value("${seckill.reconcile.scan-delay-minutes:3}") int scanDelayMinutes,
                                       @Value("${seckill.reconcile.compensation-max-retry-count:5}") int compensationMaxRetryCount,
                                       @Value("${seckill.reconcile.auto-compensation-enabled:false}") boolean autoCompensationEnabled) {
        this.taskMapper = taskMapper;
        this.voucherOrderMapper = voucherOrderMapper;
        this.voucherReconcileLogMapper = voucherReconcileLogMapper;
        this.scanBatchSize = scanBatchSize;
        this.scanDelayMinutes = scanDelayMinutes;
        this.compensationMaxRetryCount = compensationMaxRetryCount;
        this.autoCompensationEnabled = autoCompensationEnabled;
    }

    public void scanRecentRecords() {
        scanOrders();
        scanDeductLogs();
    }

    private void scanOrders() {
        QueryWrapper<VoucherOrder> wrapper = new QueryWrapper<>();
        wrapper.le("create_time", LocalDateTime.now().minusMinutes(scanDelayMinutes))
                .last("limit " + scanBatchSize);
        List<VoucherOrder> orders = voucherOrderMapper.selectList(wrapper);
        for (VoucherOrder order : orders) {
            VoucherReconcileLog deductLog = voucherReconcileLogMapper.selectOne(new QueryWrapper<VoucherReconcileLog>()
                    .eq("order_id", order.getId())
                    .eq("log_type", ReconcileLogType.DEDUCT)
                    .last("limit 1"));
            if (deductLog == null) {
                createTaskIfAbsent(SeckillReconcileAnomalyType.ORDER_PRESENT_DEDUCT_MISSING,
                        order.getId(),
                        order.getUserId(),
                        order.getVoucherId(),
                        order.getTraceId(),
                        "order exists but deduct log missing");
            }
        }
    }

    private void scanDeductLogs() {
        QueryWrapper<VoucherReconcileLog> wrapper = new QueryWrapper<>();
        wrapper.eq("log_type", ReconcileLogType.DEDUCT)
                .le("create_time", LocalDateTime.now().minusMinutes(scanDelayMinutes))
                .last("limit " + scanBatchSize);
        List<VoucherReconcileLog> logs = voucherReconcileLogMapper.selectList(wrapper);
        for (VoucherReconcileLog log : logs) {
            VoucherOrder order = voucherOrderMapper.selectById(log.getOrderId());
            if (order == null) {
                createTaskIfAbsent(SeckillReconcileAnomalyType.DEDUCT_PRESENT_ORDER_MISSING,
                        log.getOrderId(),
                        log.getUserId(),
                        log.getVoucherId(),
                        log.getTraceId(),
                        "deduct log exists but order missing");
            }
        }
    }

    private void createTaskIfAbsent(SeckillReconcileAnomalyType anomalyType,
                                    Long orderId,
                                    Long userId,
                                    Long voucherId,
                                    String traceId,
                                    String detail) {
        if (countActiveTask(anomalyType, orderId, traceId) > 0) {
            return;
        }
        SeckillReconcileTask task = autoCompensationEnabled
                && anomalyType == SeckillReconcileAnomalyType.DEDUCT_PRESENT_ORDER_MISSING
                ? SeckillReconcileTask.pendingCompensation(anomalyType, orderId, userId, voucherId, traceId, detail,
                compensationMaxRetryCount)
                : SeckillReconcileTask.pending(anomalyType, orderId, userId, voucherId, traceId, detail,
                compensationMaxRetryCount);
        taskMapper.insert(task);
    }

    private long countActiveTask(SeckillReconcileAnomalyType anomalyType, Long orderId, String traceId) {
        QueryWrapper<SeckillReconcileTask> wrapper = new QueryWrapper<>();
        wrapper.eq("anomaly_type", anomalyType.name())
                .notIn("task_status", SeckillReconcileTaskStatus.RESOLVED.name(), SeckillReconcileTaskStatus.IGNORED.name());
        if (orderId != null) {
            wrapper.eq("order_id", orderId);
        } else {
            wrapper.eq("trace_id", traceId);
        }
        Number count = taskMapper.selectCount(wrapper);
        return count == null ? 0L : count.longValue();
    }
}
