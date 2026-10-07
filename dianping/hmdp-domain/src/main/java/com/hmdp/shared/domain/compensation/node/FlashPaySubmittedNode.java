package com.hmdp.shared.domain.compensation.node;

import com.hmdp.shared.domain.compensation.model.FlashPayPayload;
import com.hmdp.shared.domain.compensation.model.FlashPayStateCode;
import com.hmdp.shared.stateengine.model.NodeResult;
import com.hmdp.shared.stateengine.model.StateTask;
import com.hmdp.shared.stateengine.service.StateContext;
import com.hmdp.shared.stateengine.service.StateNode;

/**
 * 闪赔提交节点 — 闪赔状态机的入口节点，校验用户提交的申诉原因。
 *
 * <p>定位：无忧闪赔状态机层。这是闪赔流程的第一步，
 * 也是唯一可能产生 failure 的节点。</p>
 *
 * <p>业务校验规则：
 * <ul>
 *   <li>申诉原因（reason）非空且非空白 → 自动审批通过，流转到 APPROVED</li>
 *   <li>申诉原因为空或全空白 → 返回 failure（retryable=false），
 *       由 FlashPayFailurePolicy 直接转人工审核</li>
 * </ul></p>
 *
 * <p>为什么 retryable=false：申诉原因由用户填写，为空是数据问题而非临时故障，
 * 重试没有意义，应直接由人工审核处理。</p>
 */
public class FlashPaySubmittedNode implements StateNode {

    @Override
    public String getStateCode() {
        return FlashPayStateCode.SUBMITTED.name();
    }

    /**
     * 执行申诉原因校验。
     *
     * @return 原因有效 → next(APPROVED)；原因为空 → failure(非重试)
     */
    @Override
    public NodeResult execute(StateTask task, StateContext context) {
        FlashPayPayload payload = FlashPayPayload.deserialize(task.getPayload());
        if (payload.getReason() == null || payload.getReason().trim().isEmpty()) {
            return NodeResult.failure("EMPTY_REASON", "申诉原因为空，已转人工审核", false);
        }
        return NodeResult.next(FlashPayStateCode.APPROVED.name(), "命中无忧闪赔规则，自动审批通过");
    }
}
