package com.hmdp.shared.domain.compensation.node;

import com.hmdp.shared.domain.compensation.model.FlashPayStateCode;
import com.hmdp.shared.stateengine.model.NodeResult;
import com.hmdp.shared.stateengine.model.StateTask;
import com.hmdp.shared.stateengine.service.StateContext;
import com.hmdp.shared.stateengine.service.StateNode;

/**
 * 闪赔审批通过节点 — 系统自动审批已通过，准备执行赔付。
 *
 * <p>定位：无忧闪赔状态机层。当前为自动审批通过后的过渡状态，
 * 直接流转到 COMPLETED 完成赔付。</p>
 *
 * <p>预留扩展点：后续可在此节点添加赔付金额计算、
 * 风控校验等逻辑。</p>
 */
public class FlashPayApprovedNode implements StateNode {

    @Override
    public String getStateCode() {
        return FlashPayStateCode.APPROVED.name();
    }

    @Override
    public NodeResult execute(StateTask task, StateContext context) {
        return NodeResult.next(FlashPayStateCode.COMPLETED.name(), "自动赔付处理中");
    }
}
