package com.hmdp.entity;

import com.hmdp.orderverify.model.OrderCreateRecoveryStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderCreateRecoveryTaskTest {

    @Test
    void pending_shouldInitializeManualReviewFields() {
        OrderCreateRecoveryTask task = OrderCreateRecoveryTask.pending(
                "recover-order-101",
                101L,
                11L,
                21L,
                "trace-101",
                "{\"orderId\":101,\"userId\":11,\"voucherId\":21,\"traceId\":\"trace-101\"}",
                5
        );

        assertEquals("recover-order-101", task.getTaskId());
        assertEquals(OrderCreateRecoveryStatus.PENDING.name(), task.getTaskStatus());
        assertEquals(Integer.valueOf(0), task.getRetryCount());
        assertEquals(Integer.valueOf(5), task.getMaxRetryCount());
        assertEquals("MQ retries exhausted, waiting for local recovery", task.getLastMessage());
    }
}
