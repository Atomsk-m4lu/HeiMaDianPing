package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hmdp.shared.reconciliation.model.SeckillReconcileAnomalyType;
import com.hmdp.shared.reconciliation.model.SeckillReconcileCompensationStatus;
import com.hmdp.shared.reconciliation.model.SeckillReconcileTaskStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("tb_seckill_reconcile_task")
public class SeckillReconcileTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String reconcileNo;
    private Long orderId;
    private Long userId;
    private Long voucherId;
    private String traceId;
    private String anomalyType;
    private String taskStatus;
    private String compensationStatus;
    private String anomalyDetail;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime nextExecuteTime;
    private LocalDateTime lastExecuteTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static SeckillReconcileTask pending(SeckillReconcileAnomalyType anomalyType,
                                               Long orderId,
                                               Long userId,
                                               Long voucherId,
                                               String traceId,
                                               String anomalyDetail,
                                               int maxRetryCount) {
        return create(anomalyType, orderId, userId, voucherId, traceId, anomalyDetail, maxRetryCount,
                SeckillReconcileCompensationStatus.NOT_NEEDED);
    }

    public static SeckillReconcileTask pendingCompensation(SeckillReconcileAnomalyType anomalyType,
                                                           Long orderId,
                                                           Long userId,
                                                           Long voucherId,
                                                           String traceId,
                                                           String anomalyDetail,
                                                           int maxRetryCount) {
        return create(anomalyType, orderId, userId, voucherId, traceId, anomalyDetail, maxRetryCount,
                SeckillReconcileCompensationStatus.WAITING);
    }

    private static SeckillReconcileTask create(SeckillReconcileAnomalyType anomalyType,
                                               Long orderId,
                                               Long userId,
                                               Long voucherId,
                                               String traceId,
                                               String anomalyDetail,
                                               int maxRetryCount,
                                               SeckillReconcileCompensationStatus compensationStatus) {
        LocalDateTime now = LocalDateTime.now();
        String bizId = orderId != null ? String.valueOf(orderId) : traceId;
        return new SeckillReconcileTask()
                .setReconcileNo("reconcile-" + anomalyType.name() + "-" + bizId)
                .setOrderId(orderId)
                .setUserId(userId)
                .setVoucherId(voucherId)
                .setTraceId(traceId)
                .setAnomalyType(anomalyType.name())
                .setTaskStatus(SeckillReconcileTaskStatus.PENDING.name())
                .setCompensationStatus(compensationStatus.name())
                .setAnomalyDetail(anomalyDetail)
                .setRetryCount(0)
                .setMaxRetryCount(maxRetryCount)
                .setNextExecuteTime(now)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setCreateTime(now)
                .setUpdateTime(now);
    }
}
