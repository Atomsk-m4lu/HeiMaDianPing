package com.hmdp.service.impl;

import com.hmdp.entity.OrderCreateVerifyTask;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.VoucherReconcileLog;
import com.hmdp.mapper.OrderCreateVerifyTaskMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mapper.VoucherReconcileLogMapper;
import com.hmdp.orderverify.model.OrderCreateVerifyStatus;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.shared.reconciliation.model.ReconcileLogType;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCreateVerificationServiceTest {

    @Test
    void verifyDueTasks_shouldMarkSuccessWhenOrderAndDeductLogExist() {
        OrderCreateVerifyTaskMapper taskMapper = mock(OrderCreateVerifyTaskMapper.class);
        VoucherOrderMapper orderMapper = mock(VoucherOrderMapper.class);
        VoucherReconcileLogMapper logMapper = mock(VoucherReconcileLogMapper.class);
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        OrderCreateRecoveryService recoveryService = mock(OrderCreateRecoveryService.class);
        OrderCreateVerificationService service =
                new OrderCreateVerificationService(taskMapper, orderMapper, logMapper, voucherOrderService, recoveryService, 5, 10);
        OrderCreateVerifyTask task = OrderCreateVerifyTask.pending("verify-order-101", 101L, 11L, 21L, "trace-101", 5);
        when(taskMapper.selectList(any())).thenReturn(Collections.singletonList(task));
        when(orderMapper.selectById(101L)).thenReturn(new VoucherOrder().setId(101L));
        when(logMapper.selectOne(any())).thenReturn(new VoucherReconcileLog().setTraceId("trace-101").setLogType(ReconcileLogType.DEDUCT));

        service.verifyDueTasks();

        assertEquals(OrderCreateVerifyStatus.SUCCESS.name(), task.getTaskStatus());
        verify(taskMapper, atLeastOnce()).updateById(task);
    }

    @Test
    void verifyDueTasks_shouldNotAutoRollbackWhenRetryExhausted() {
        OrderCreateVerifyTaskMapper taskMapper = mock(OrderCreateVerifyTaskMapper.class);
        VoucherOrderMapper orderMapper = mock(VoucherOrderMapper.class);
        VoucherReconcileLogMapper logMapper = mock(VoucherReconcileLogMapper.class);
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        OrderCreateRecoveryService recoveryService = mock(OrderCreateRecoveryService.class);
        OrderCreateVerificationService service =
                new OrderCreateVerificationService(taskMapper, orderMapper, logMapper, voucherOrderService, recoveryService, 0, 10);
        OrderCreateVerifyTask task = OrderCreateVerifyTask.pending("verify-order-101", 101L, 11L, 21L, "trace-101", 0);
        when(taskMapper.selectList(any())).thenReturn(Collections.singletonList(task));
        when(orderMapper.selectById(101L)).thenReturn(null);
        when(logMapper.selectOne(any())).thenReturn(null);

        service.verifyDueTasks();

        assertEquals(OrderCreateVerifyStatus.FAILED.name(), task.getTaskStatus());
        verify(voucherOrderService, never()).rollbackSeckillVoucherPlus(any(VoucherOrder.class));
        verify(recoveryService).createRecoveryTask(any(), any(), anyInt());
    }

    @Test
    void autowiredConstructor_shouldHandOffToRecoveryWhenRetryExhausted() {
        OrderCreateVerifyTaskMapper taskMapper = mock(OrderCreateVerifyTaskMapper.class);
        VoucherOrderMapper orderMapper = mock(VoucherOrderMapper.class);
        VoucherReconcileLogMapper logMapper = mock(VoucherReconcileLogMapper.class);
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        OrderCreateRecoveryService recoveryService = mock(OrderCreateRecoveryService.class);
        // 生产用的 5 参构造器（@Autowired）：修复前这里 recoveryService 固定传 null，接力会断
        OrderCreateVerificationService service =
                new OrderCreateVerificationService(taskMapper, orderMapper, logMapper, voucherOrderService, recoveryService);
        OrderCreateVerifyTask task = OrderCreateVerifyTask.pending("verify-order-101", 101L, 11L, 21L, "trace-101", 0);
        when(taskMapper.selectList(any())).thenReturn(Collections.singletonList(task));
        when(orderMapper.selectById(101L)).thenReturn(null);
        when(logMapper.selectOne(any())).thenReturn(null);

        service.verifyDueTasks();

        assertEquals(OrderCreateVerifyStatus.FAILED.name(), task.getTaskStatus());
        verify(recoveryService).createRecoveryTask(any(), any(), anyInt());
    }
}
