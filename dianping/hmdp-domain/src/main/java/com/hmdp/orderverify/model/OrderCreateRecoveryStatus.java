package com.hmdp.orderverify.model;

public enum OrderCreateRecoveryStatus {
    PENDING,
    PROCESSING,
    RETRYING,
    SUCCESS,
    MANUAL_REVIEW,
    CLOSED
}
