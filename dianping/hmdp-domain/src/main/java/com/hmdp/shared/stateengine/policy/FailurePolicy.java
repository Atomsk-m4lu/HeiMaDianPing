package com.hmdp.shared.stateengine.policy;

import com.hmdp.shared.stateengine.model.NodeResult;
import com.hmdp.shared.stateengine.model.RetryDecision;
import com.hmdp.shared.stateengine.model.StateTask;

/**
 * 失败策略接口 — 定义节点执行失败后的处理决策逻辑。
 *
 * <p>定位：核心引擎层。每个状态机可以有自己的 FailurePolicy 实现，
 * 根据业务语义决定失败后是重试还是转人工。</p>
 *
 * <p>典型实现：
 * <ul>
 *   <li>秒杀对账：失败后始终返回 retry（由 RetryPolicy 限制次数）</li>
 *   <li>无忧闪赔：失败后始终返回 manualReview（业务校验失败需人工介入）</li>
 * </ul></p>
 *
 * <p>调用链：StateEngine → FailurePolicy.onFailure() → RetryDecision → 引擎决定最终处理</p>
 */
public interface FailurePolicy {

    /**
     * 节点执行失败时的决策回调。
     *
     * @param task   失败时刻的任务快照（含 retryCount 等信息）
     * @param result 节点的失败结果（含 errorCode、message 等）
     * @return 重试决策 — 建议重试还是转人工
     */
    RetryDecision onFailure(StateTask task, NodeResult result);
}
