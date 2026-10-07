package com.hmdp.shared.stateengine.model;

import lombok.Getter;

/**
 * 重试决策 — FailurePolicy 的返回值，告诉引擎节点失败后该如何处理。
 *
 * <p>定位：核心引擎层。两个工厂方法对应两种处理路径：
 * <ul>
 *   <li>{@link #retry(String)} — <b>建议重试</b>，引擎会结合 RetryPolicy 判断是否真能重试
 *       <br>如果可以：标记 FAILED，等待外部定时任务 resume()
 *       <br>如果不可（超次数）：转 MANUAL_REVIEW</li>
 *   <li>{@link #manualReview(String)} — <b>转人工审核</b>，直接标记 MANUAL_REVIEW
 *       <br>用于不可自动恢复的场景（如数据校验不通过）</li>
 * </ul></p>
 */
@Getter
public class RetryDecision {

    /** 是否需要转人工审核 */
    private final boolean manualReview;
    /** 决策描述消息 */
    private final String message;

    private RetryDecision(boolean manualReview, String message) {
        this.manualReview = manualReview;
        this.message = message;
    }

    /**
     * 工厂方法 — 建议转人工审核。
     * <p>引擎收到后直接标记 MANUAL_REVIEW，不经过 RetryPolicy 判断。</p>
     */
    public static RetryDecision manualReview(String message) {
        return new RetryDecision(true, message);
    }

    /**
     * 工厂方法 — 建议重试。
     * <p>引擎收到后还会结合 RetryPolicy.canRetry() 判断
     * 是否真的可以重试，若超过最大重试次数仍会转人工。</p>
     */
    public static RetryDecision retry(String message) {
        return new RetryDecision(false, message);
    }
}
