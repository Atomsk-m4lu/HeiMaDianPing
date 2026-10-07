package com.hmdp.shared.stateengine.policy;

/**
 * 重试策略 — 基于最大重试次数的简单限流器。
 *
 * <p>定位：核心引擎层。当节点执行失败且 FailurePolicy 建议重试时，
 * RetryPolicy 检查任务的 retryCount 是否已达上限，防止无限重试。</p>
 *
 * <p>决策链：节点失败 → FailurePolicy 建议重试 → RetryPolicy 检查次数 → 决定标记 FAILED 还是转 MANUAL_REVIEW</p>
 */
public class RetryPolicy {

    /** 最大重试次数 */
    private final int maxRetryCount;

    /**
     * @param maxRetryCount 任务允许的最大重试次数
     */
    public RetryPolicy(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    /**
     * 判断任务是否还能重试。
     *
     * @return true 如果当前重试次数 < 最大重试次数
     */
    public boolean canRetry(com.hmdp.shared.stateengine.model.StateTask task) {
        return task.getRetryCount() < maxRetryCount;
    }
}
