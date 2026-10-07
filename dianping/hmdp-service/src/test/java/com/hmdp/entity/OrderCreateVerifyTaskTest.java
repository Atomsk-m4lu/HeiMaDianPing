package com.hmdp.entity;

import com.hmdp.orderverify.model.OrderCreateVerifyStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderCreateVerifyTaskTest {

    @Test
    void shouldBuildPendingTaskForOrderCreateVerification() {
        OrderCreateVerifyTask task = OrderCreateVerifyTask.pending(
                "verify-order-101",
                101L,
                11L,
                21L,
                "trace-101",
                5
        );

        assertEquals("verify-order-101", task.getTaskId());
        assertEquals(Long.valueOf(101L), task.getOrderId());
        assertEquals(OrderCreateVerifyStatus.PENDING.name(), task.getTaskStatus());
        assertEquals(Integer.valueOf(5), task.getMaxRetryCount());
    }
}
