package com.hmdp.service.impl;

import com.hmdp.dto.SeckillVoucherOrderMessage;
import com.hmdp.entity.OrderCreateRecoveryTask;
import com.hmdp.mapper.OrderCreateRecoveryTaskMapper;
import com.hmdp.metrics.MetricsRecorder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.VoucherOrderCreateResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCreateRecoveryServiceTest {

    @Test
    void createRecoveryTask_shouldPersistPendingRecoveryTask() {
        OrderCreateRecoveryTaskMapper taskMapper = mock(OrderCreateRecoveryTaskMapper.class);
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        OrderCreateRecoveryService service = new OrderCreateRecoveryService(taskMapper, voucherOrderService, 5, 10, 5);
        SeckillVoucherOrderMessage message = new SeckillVoucherOrderMessage();
        message.setOrderId(101L);
        message.setUserId(11L);
        message.setVoucherId(21L);
        message.setTraceId("trace-101");

        service.createRecoveryTask(message, "{\"orderId\":101}", 3);

        verify(taskMapper).insert(any(OrderCreateRecoveryTask.class));
    }

    @Test
    void processDueTasks_shouldMoveToManualReviewAfterRetryExhausted() {
        OrderCreateRecoveryTaskMapper taskMapper = mock(OrderCreateRecoveryTaskMapper.class);
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        OrderCreateRecoveryService service = new OrderCreateRecoveryService(taskMapper, voucherOrderService, 0, 10, 5);
        ReflectionTestUtils.setField(service, "metricsRecorder", new MetricsRecorder(new SimpleMeterRegistry()));
        OrderCreateRecoveryTask task = OrderCreateRecoveryTask.pending(
                "recover-order-101",
                101L,
                11L,
                21L,
                "trace-101",
                "{\"orderId\":101,\"userId\":11,\"voucherId\":21,\"traceId\":\"trace-101\"}",
                0
        );
        when(taskMapper.selectList(any())).thenReturn(Collections.singletonList(task));
        when(voucherOrderService.createVoucherOrderPlus(any(SeckillVoucherOrderMessage.class)))
                .thenThrow(new RuntimeException("db timeout"));

        service.processDueTasks();

        assertEquals("MANUAL_REVIEW", task.getTaskStatus());
        assertEquals("PENDING", task.getAlarmStatus());
        assertNotNull(task.getManualDeadline());
        assertTrue(Duration.between(LocalDateTime.now(), task.getManualDeadline()).toMinutes() <= 5);
        verify(taskMapper, atLeastOnce()).updateById(task);
    }

    @Test
    void processDueTasks_shouldMarkSuccessWhenRecoveryCreateSucceeds() {
        OrderCreateRecoveryTaskMapper taskMapper = mock(OrderCreateRecoveryTaskMapper.class);
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        OrderCreateRecoveryService service = new OrderCreateRecoveryService(taskMapper, voucherOrderService, 5, 10, 5);
        OrderCreateRecoveryTask task = OrderCreateRecoveryTask.pending(
                "recover-order-101",
                101L,
                11L,
                21L,
                "trace-101",
                "{\"orderId\":101,\"userId\":11,\"voucherId\":21,\"traceId\":\"trace-101\"}",
                5
        );
        when(taskMapper.selectList(any())).thenReturn(Collections.singletonList(task));
        when(voucherOrderService.createVoucherOrderPlus(any(SeckillVoucherOrderMessage.class)))
                .thenReturn(VoucherOrderCreateResult.CREATED);

        service.processDueTasks();

        assertEquals("SUCCESS", task.getTaskStatus());
        verify(taskMapper, atLeastOnce()).updateById(task);
    }

    @Test
    void abandonRecoveryTask_shouldRollbackReservationAndCloseTask() {
        OrderCreateRecoveryTaskMapper taskMapper = mock(OrderCreateRecoveryTaskMapper.class);
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        OrderCreateRecoveryService service = new OrderCreateRecoveryService(taskMapper, voucherOrderService, 5, 10, 5);
        OrderCreateRecoveryTask task = OrderCreateRecoveryTask.pending(
                "recover-order-101",
                101L,
                11L,
                21L,
                "trace-101",
                "{\"orderId\":101,\"userId\":11,\"voucherId\":21,\"traceId\":\"trace-101\"}",
                5
        ).setTaskStatus("MANUAL_REVIEW");

        service.abandonAndRollback(task);

        assertEquals("CLOSED", task.getTaskStatus());
        verify(voucherOrderService).rollbackSeckillVoucherPlus(any());
        verify(taskMapper, atLeastOnce()).updateById(task);
    }

    @Test
    void markManualSuccess_shouldMarkTaskSuccessWithoutRollback() {
        OrderCreateRecoveryTaskMapper taskMapper = mock(OrderCreateRecoveryTaskMapper.class);
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        OrderCreateRecoveryService service = new OrderCreateRecoveryService(taskMapper, voucherOrderService, 5, 10, 5);
        OrderCreateRecoveryTask task = OrderCreateRecoveryTask.pending(
                "recover-order-101",
                101L,
                11L,
                21L,
                "trace-101",
                "{\"orderId\":101,\"userId\":11,\"voucherId\":21,\"traceId\":\"trace-101\"}",
                5
        ).setTaskStatus("MANUAL_REVIEW");

        service.markManualSuccess(task, "manual order repair completed");

        assertEquals("SUCCESS", task.getTaskStatus());
        verify(taskMapper, atLeastOnce()).updateById(task);
    }
}
