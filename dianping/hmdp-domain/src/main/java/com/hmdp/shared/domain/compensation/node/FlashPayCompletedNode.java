package com.hmdp.shared.domain.compensation.node;

import com.hmdp.shared.domain.compensation.model.FlashPayStateCode;
import com.hmdp.shared.stateengine.model.NodeResult;
import com.hmdp.shared.stateengine.model.StateTask;
import com.hmdp.shared.stateengine.service.StateContext;
import com.hmdp.shared.stateengine.service.StateNode;

/**
 * 闪赔完成节点 — 闪赔状态机的终态节点。
 *
 * <p>定位：无忧闪赔状态机层。当闪赔流程走完 SUBMITTED → APPROVED → COMPLETED
 * 路径后，由本节点返回 terminalSuccess 终止状态机。</p>
 *
 * <p>注意：MANUAL_REVIEW 也是终态，但通过 FailurePolicy 路径到达，
 * 不经过此节点。</p>
 */
public class FlashPayCompletedNode implements StateNode {

    @Override
    public String getStateCode() {
        return FlashPayStateCode.COMPLETED.name();
    }

    @Override
    public NodeResult execute(StateTask task, StateContext context) {
        return NodeResult.terminalSuccess("自动赔付已完成");
    }
}
