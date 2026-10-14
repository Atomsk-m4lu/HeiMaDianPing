package com.hmdp.listener;

import com.hmdp.dto.SeckillVoucherOrderMessage;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.impl.OrderCreateRecoveryService;
import com.hmdp.service.impl.RollbackFailureService;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillPlusOrderListenerRecoveryTest {

    @Test
    void processMessage_shouldThrowDuringEarlyMqRetryForInfrastructureFailure() {
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        OrderCreateRecoveryService recoveryService = mock(OrderCreateRecoveryService.class);
        SeckillPlusOrderListener listener = new SeckillPlusOrderListener(voucherOrderService, recoveryService, mock(RollbackFailureService.class));
        MessageExt msg = mock(MessageExt.class);
        when(msg.getBody()).thenReturn(json().getBytes(StandardCharsets.UTF_8));
        when(msg.getReconsumeTimes()).thenReturn(1);
        when(voucherOrderService.createVoucherOrderPlus(any(SeckillVoucherOrderMessage.class)))
                .thenThrow(new RuntimeException("db timeout"));

        assertThrows(RuntimeException.class, () -> listener.processMessage(msg));

        verify(recoveryService, never()).createRecoveryTask(any(), any(), anyInt());
        verify(voucherOrderService, never()).rollbackSeckillVoucherPlus(any());
    }

    @Test
    void processMessage_shouldCreateRecoveryTaskAfterMqRetryThreshold() {
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        OrderCreateRecoveryService recoveryService = mock(OrderCreateRecoveryService.class);
        SeckillPlusOrderListener listener = new SeckillPlusOrderListener(voucherOrderService, recoveryService, mock(RollbackFailureService.class));
        MessageExt msg = mock(MessageExt.class);
        when(msg.getBody()).thenReturn(json().getBytes(StandardCharsets.UTF_8));
        when(msg.getReconsumeTimes()).thenReturn(3);
        when(voucherOrderService.createVoucherOrderPlus(any(SeckillVoucherOrderMessage.class)))
                .thenThrow(new RuntimeException("db timeout"));

        listener.processMessage(msg);

        verify(recoveryService).createRecoveryTask(any(SeckillVoucherOrderMessage.class), eq(json()), eq(3));
        verify(voucherOrderService, never()).rollbackSeckillVoucherPlus(any());
    }

    private String json() {
        return "{\"orderId\":101,\"userId\":9,\"voucherId\":7,\"traceId\":\"trace-101\"}";
    }
}
