package com.hmdp.shared.domain.compensation.node;

import com.hmdp.shared.domain.compensation.model.FlashPayStateCode;
import com.hmdp.shared.stateengine.model.NodeResult;
import com.hmdp.shared.stateengine.model.StateTask;
import com.hmdp.shared.stateengine.service.StateContext;
import com.hmdp.shared.stateengine.service.StateNode;

/**
 * 闪赔人工审核节点 — 转人工审核的入口状态。
 *
 * <p>定位：无忧闪赔状态机层。当 SUBMITTED 节点校验失败（申诉原因为空）时，
 * FailurePolicy 将任务标记为 MANUAL_REVIEW 并流转到此节点。</p>
 *
 * <p>该节点返回 terminalSuccess，表示从状态机角度任务已结束，
 * 人工审核结果由外部系统处理，不再回到状态机。</p>
 */
public class FlashPayManualReviewNode implements StateNode {

    @Override
    public String getStateCode() {
        return FlashPayStateCode.MANUAL_REVIEW.name();
    }

    @Override
    public NodeResult execute(StateTask task, StateContext context) {
        return NodeResult.terminalSuccess("已转人工审核");
    }
}
