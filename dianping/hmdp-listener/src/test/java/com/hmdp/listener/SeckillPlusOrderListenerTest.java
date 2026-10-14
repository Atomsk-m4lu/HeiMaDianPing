package com.hmdp.listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.SeckillVoucherOrderMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.VoucherOrderCreateResult;
import com.hmdp.service.impl.OrderCreateRecoveryService;
import com.hmdp.service.impl.RollbackFailureService;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillPlusOrderListenerTest {

    @Test
    void onMessage_shouldCreateVoucherOrder() {
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        OrderCreateRecoveryService recoveryService = mock(OrderCreateRecoveryService.class);
        SeckillPlusOrderListener listener = new SeckillPlusOrderListener(voucherOrderService, recoveryService, mock(RollbackFailureService.class));
        SeckillVoucherOrderMessage message = message();
        when(voucherOrderService.createVoucherOrderPlus(message)).thenReturn(VoucherOrderCreateResult.CREATED);

        listener.onMessage(JSONUtil.toJsonStr(message));

        verify(voucherOrderService).createVoucherOrderPlus(message);
    }

    @Test
    void onMessage_shouldRollbackAndNotThrowWhenStockSyncFails() {
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        OrderCreateRecoveryService recoveryService = mock(OrderCreateRecoveryService.class);
        SeckillPlusOrderListener listener = new SeckillPlusOrderListener(voucherOrderService, recoveryService, mock(RollbackFailureService.class));
        SeckillVoucherOrderMessage message = message();
        when(voucherOrderService.createVoucherOrderPlus(message)).thenReturn(VoucherOrderCreateResult.OUT_OF_STOCK);

        listener.onMessage(JSONUtil.toJsonStr(message));

        verify(voucherOrderService).createVoucherOrderPlus(message);
        verify(voucherOrderService).rollbackSeckillVoucherPlus(argThat(order ->
                Long.valueOf(101L).equals(order.getId())
                        && Long.valueOf(9L).equals(order.getUserId())
                        && Long.valueOf(7L).equals(order.getVoucherId())
                        && "trace-101".equals(order.getTraceId())));
    }

    @Test
    void onMessage_shouldIgnoreDuplicateAndNotRollback() {
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        OrderCreateRecoveryService recoveryService = mock(OrderCreateRecoveryService.class);
        SeckillPlusOrderListener listener = new SeckillPlusOrderListener(voucherOrderService, recoveryService, mock(RollbackFailureService.class));
        SeckillVoucherOrderMessage message = message();
        when(voucherOrderService.createVoucherOrderPlus(message)).thenReturn(VoucherOrderCreateResult.DUPLICATE);

        listener.onMessage(JSONUtil.toJsonStr(message));

        verify(voucherOrderService).createVoucherOrderPlus(message);
        verify(voucherOrderService, never()).rollbackSeckillVoucherPlus(any(VoucherOrder.class));
    }

    @Test
    void onMessage_shouldThrowWhenDbExceptionOccursBeforeMqRetryThreshold() {
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        OrderCreateRecoveryService recoveryService = mock(OrderCreateRecoveryService.class);
        SeckillPlusOrderListener listener = new SeckillPlusOrderListener(voucherOrderService, recoveryService, mock(RollbackFailureService.class));
        SeckillVoucherOrderMessage message = message();
        when(voucherOrderService.createVoucherOrderPlus(message))
                .thenThrow(new RuntimeException("Connection pool exhausted"));

        assertThrows(RuntimeException.class, () -> listener.onMessage(JSONUtil.toJsonStr(message)));

        verify(voucherOrderService).createVoucherOrderPlus(message);
        verify(voucherOrderService, never()).rollbackSeckillVoucherPlus(any(VoucherOrder.class));
    }

    @Test
    void batchConsume_shouldReturnSuccessWhenAllMessagesProcessed() throws Exception {
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        when(voucherOrderService.createVoucherOrderPlus(any())).thenReturn(VoucherOrderCreateResult.CREATED);
        OrderCreateRecoveryService recoveryService = mock(OrderCreateRecoveryService.class);

        SeckillPlusOrderListener listener = new SeckillPlusOrderListener(voucherOrderService, recoveryService, mock(RollbackFailureService.class));
        MessageListenerConcurrently batchListener = captureBatchListener(listener);

        MessageExt msg1 = createMockMessage(json(1L, 1L, 1L, "t1"));
        MessageExt msg2 = createMockMessage(json(2L, 2L, 2L, "t2"));
        MessageExt msg3 = createMockMessage(json(3L, 3L, 3L, "t3"));

        ConsumeConcurrentlyStatus status = batchListener.consumeMessage(Arrays.asList(msg1, msg2, msg3), null);

        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, status);
        verify(voucherOrderService, times(3)).createVoucherOrderPlus(any());
    }

    @Test
    void batchConsume_shouldReturnSuccessEvenWhenSomeMessagesFail() throws Exception {
        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        when(voucherOrderService.createVoucherOrderPlus(any()))
                .thenReturn(VoucherOrderCreateResult.CREATED)
                .thenReturn(VoucherOrderCreateResult.OUT_OF_STOCK)
                .thenReturn(VoucherOrderCreateResult.CREATED);
        OrderCreateRecoveryService recoveryService = mock(OrderCreateRecoveryService.class);

        SeckillPlusOrderListener listener = new SeckillPlusOrderListener(voucherOrderService, recoveryService, mock(RollbackFailureService.class));
        MessageListenerConcurrently batchListener = captureBatchListener(listener);

        MessageExt msg1 = createMockMessage(json(1L, 1L, 1L, "t1"));
        MessageExt msg2 = createMockMessage(json(2L, 2L, 2L, "t2"));
        MessageExt msg3 = createMockMessage(json(3L, 3L, 3L, "t3"));

        ConsumeConcurrentlyStatus status = batchListener.consumeMessage(Arrays.asList(msg1, msg2, msg3), null);

        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, status);
        verify(voucherOrderService, times(3)).createVoucherOrderPlus(any());
        verify(voucherOrderService).rollbackSeckillVoucherPlus(any());
    }

    @Test
    void batchConsume_shouldProcessMessagesConcurrently() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        IVoucherOrderService voucherOrderService = mock(IVoucherOrderService.class);
        when(voucherOrderService.createVoucherOrderPlus(any())).thenAnswer(invocation -> {
            int current = concurrentCount.incrementAndGet();
            maxConcurrent.updateAndGet(v -> Math.max(v, current));
            latch.countDown();
            latch.await(5, TimeUnit.SECONDS);
            concurrentCount.decrementAndGet();
            return VoucherOrderCreateResult.CREATED;
        });
        OrderCreateRecoveryService recoveryService = mock(OrderCreateRecoveryService.class);

        SeckillPlusOrderListener listener = new SeckillPlusOrderListener(voucherOrderService, recoveryService, mock(RollbackFailureService.class));
        MessageListenerConcurrently batchListener = captureBatchListener(listener);

        MessageExt msg1 = createMockMessage(json(1L, 1L, 1L, "t1"));
        MessageExt msg2 = createMockMessage(json(2L, 2L, 2L, "t2"));
        MessageExt msg3 = createMockMessage(json(3L, 3L, 3L, "t3"));

        ConsumeConcurrentlyStatus status = batchListener.consumeMessage(Arrays.asList(msg1, msg2, msg3), null);

        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, status);
        assertTrue(maxConcurrent.get() >= 2, "expected at least 2 concurrent, actual=" + maxConcurrent.get());
    }

    private MessageListenerConcurrently captureBatchListener(SeckillPlusOrderListener listener) {
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        listener.prepareStart(consumer);

        ArgumentCaptor<MessageListenerConcurrently> captor = ArgumentCaptor.forClass(MessageListenerConcurrently.class);
        verify(consumer).setConsumeMessageBatchMaxSize(32);
        verify(consumer).setMessageListener(captor.capture());
        return captor.getValue();
    }

    private MessageExt createMockMessage(String body) {
        MessageExt msg = mock(MessageExt.class);
        when(msg.getBody()).thenReturn(body.getBytes());
        return msg;
    }

    private String json(Long orderId, Long userId, Long voucherId, String traceId) {
        return "{\"orderId\":" + orderId + ",\"userId\":" + userId + ",\"voucherId\":" + voucherId + ",\"traceId\":\"" + traceId + "\"}";
    }

    private SeckillVoucherOrderMessage message() {
        SeckillVoucherOrderMessage message = new SeckillVoucherOrderMessage();
        message.setOrderId(101L);
        message.setUserId(9L);
        message.setVoucherId(7L);
        message.setTraceId("trace-101");
        return message;
    }
}
