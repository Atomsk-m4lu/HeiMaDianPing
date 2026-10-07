package com.hmdp.shared.domain.compensation.policy;

import com.hmdp.shared.stateengine.model.NodeResult;
import com.hmdp.shared.stateengine.model.RetryDecision;
import com.hmdp.shared.stateengine.model.StateTask;
import com.hmdp.shared.stateengine.policy.FailurePolicy;

/**
 * 无忧闪赔失败策略 — 失败后始终转人工审核。
 *
 * <p>定位：无忧闪赔状态机层。闪赔场景下失败原因是业务数据问题
 * （如申诉原因为空），重试无法自动修复，因此直接转人工。</p>
 *
 * <p>与秒杀对账的差异：秒杀对账的补偿失败通常是临时性故障（依赖服务暂不可用），
 * 适合重试；闪赔是数据校验问题，重试无意义。</p>
 */
public class FlashPayFailurePolicy implements FailurePolicy {

    @Override
    public RetryDecision onFailure(StateTask task, NodeResult result) {
        return RetryDecision.manualReview(result.getErrorMessage());
    }
}
