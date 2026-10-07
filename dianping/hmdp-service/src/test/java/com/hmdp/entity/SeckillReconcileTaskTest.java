package com.hmdp.entity;

import com.hmdp.shared.reconciliation.model.SeckillReconcileAnomalyType;
import com.hmdp.shared.reconciliation.model.SeckillReconcileCompensationStatus;
import com.hmdp.shared.reconciliation.model.SeckillReconcileTaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeckillReconcileTaskTest {

    @Test
    void pendingShouldBuildManualReviewTask() {
        SeckillReconcileTask task = SeckillReconcileTask.pending(
                SeckillReconcileAnomalyType.ORDER_PRESENT_DEDUCT_MISSING,
                101L,
                11L,
                21L,
                "trace-101",
                "order exists but deduct missing",
                5
        );

        assertEquals("reconcile-ORDER_PRESENT_DEDUCT_MISSING-101", task.getReconcileNo());
        assertEquals(SeckillReconcileTaskStatus.PENDING.name(), task.getTaskStatus());
        assertEquals(SeckillReconcileCompensationStatus.NOT_NEEDED.name(), task.getCompensationStatus());
    }

    @Test
    void pendingCompensationShouldBuildWaitingTask() {
        SeckillReconcileTask task = SeckillReconcileTask.pendingCompensation(
                SeckillReconcileAnomalyType.DEDUCT_PRESENT_ORDER_MISSING,
                101L,
                11L,
                21L,
                "trace-101",
                "deduct exists but order missing",
                5
        );

        assertEquals("reconcile-DEDUCT_PRESENT_ORDER_MISSING-101", task.getReconcileNo());
        assertEquals(SeckillReconcileCompensationStatus.WAITING.name(), task.getCompensationStatus());
    }
}
