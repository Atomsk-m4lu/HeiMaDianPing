package com.hmdp.service.impl;

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
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillReconcileCompensationServiceTest {

    @Test
    void processDueTasks_shouldDoNothingWhenAutoCompensationDisabled() {
        SeckillReconcileTaskMapper taskMapper = mock(SeckillReconcileTaskMapper.class);
        VoucherOrderMapper orderMapper = mock(VoucherOrderMapper.class);
        VoucherReconcileLogMapper logMapper = mock(VoucherReconcileLogMapper.class);
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        SeckillReconcileCompensationService service =
                new SeckillReconcileCompensationService(taskMapper, orderMapper, logMapper, voucherOrderService, false, 20, 5);

        service.processDueTasks();

        verify(taskMapper, never()).selectList(any());
        verify(voucherOrderService, never()).rollbackSeckillVoucherPlus(any(VoucherOrder.class));
    }

    @Test
    void processDueTasks_shouldRollbackWhenDeductExistsButOrderMissing() {
        SeckillReconcileTaskMapper taskMapper = mock(SeckillReconcileTaskMapper.class);
        VoucherOrderMapper orderMapper = mock(VoucherOrderMapper.class);
        VoucherReconcileLogMapper logMapper = mock(VoucherReconcileLogMapper.class);
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        SeckillReconcileCompensationService service =
                new SeckillReconcileCompensationService(taskMapper, orderMapper, logMapper, voucherOrderService, true, 20, 5);

        SeckillReconcileTask task = SeckillReconcileTask.pendingCompensation(
                SeckillReconcileAnomalyType.DEDUCT_PRESENT_ORDER_MISSING,
                101L,
                11L,
                21L,
                "trace-101",
                "deduct exists but order missing",
                5
        );
        when(taskMapper.selectList(any())).thenReturn(Collections.singletonList(task));
        when(orderMapper.selectById(101L)).thenReturn(null);
        when(logMapper.selectOne(any()))
                .thenReturn(new VoucherReconcileLog().setTraceId("trace-101").setLogType(ReconcileLogType.DEDUCT))
                .thenReturn(null);
        // CAS 抢占成功
        when(taskMapper.update(any(), any())).thenReturn(1);

        service.processDueTasks();

        assertEquals(SeckillReconcileTaskStatus.RESOLVED.name(), task.getTaskStatus());
        assertEquals(SeckillReconcileCompensationStatus.DONE.name(), task.getCompensationStatus());
        verify(voucherOrderService).rollbackSeckillVoucherPlus(any(VoucherOrder.class));
        verify(taskMapper, atLeastOnce()).updateById(task);
    }

    @Test
    void processDueTasks_shouldNotRollbackWhenPreCheckNoLongerMatches() {
        SeckillReconcileTaskMapper taskMapper = mock(SeckillReconcileTaskMapper.class);
        VoucherOrderMapper orderMapper = mock(VoucherOrderMapper.class);
        VoucherReconcileLogMapper logMapper = mock(VoucherReconcileLogMapper.class);
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        SeckillReconcileCompensationService service =
                new SeckillReconcileCompensationService(taskMapper, orderMapper, logMapper, voucherOrderService, true, 20, 5);

        SeckillReconcileTask task = SeckillReconcileTask.pendingCompensation(
                SeckillReconcileAnomalyType.DEDUCT_PRESENT_ORDER_MISSING,
                101L,
                11L,
                21L,
                "trace-101",
                "deduct exists but order missing",
                5
        );
        when(taskMapper.selectList(any())).thenReturn(Collections.singletonList(task));
        when(orderMapper.selectById(101L)).thenReturn(new VoucherOrder().setId(101L));
        // CAS 抢占成功
        when(taskMapper.update(any(), any())).thenReturn(1);

        service.processDueTasks();

        assertEquals(SeckillReconcileTaskStatus.RESOLVED.name(), task.getTaskStatus());
        verify(voucherOrderService, never()).rollbackSeckillVoucherPlus(any(VoucherOrder.class));
    }

    @Test
    void processDueTasks_shouldSkipWhenClaimFails() {
        SeckillReconcileTaskMapper taskMapper = mock(SeckillReconcileTaskMapper.class);
        VoucherOrderMapper orderMapper = mock(VoucherOrderMapper.class);
        VoucherReconcileLogMapper logMapper = mock(VoucherReconcileLogMapper.class);
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        SeckillReconcileCompensationService service =
                new SeckillReconcileCompensationService(taskMapper, orderMapper, logMapper, voucherOrderService, true, 20, 5);

        SeckillReconcileTask task = SeckillReconcileTask.pendingCompensation(
                SeckillReconcileAnomalyType.DEDUCT_PRESENT_ORDER_MISSING,
                101L, 11L, 21L, "trace-101", "deduct exists but order missing", 5);
        when(taskMapper.selectList(any())).thenReturn(Collections.singletonList(task));
        // CAS 抢占失败（已被其他实例/上一轮处理）→ 不执行预检查、不回滚
        when(taskMapper.update(any(), any())).thenReturn(0);

        service.processDueTasks();

        verify(orderMapper, never()).selectById(any());
        verify(voucherOrderService, never()).rollbackSeckillVoucherPlus(any(VoucherOrder.class));
        verify(taskMapper, never()).updateById(any(SeckillReconcileTask.class));
    }
}
