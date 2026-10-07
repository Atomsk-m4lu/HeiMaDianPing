package com.hmdp.shared.domain.compensation.machine;

import com.hmdp.shared.domain.compensation.model.FlashPayStateCode;
import com.hmdp.shared.domain.compensation.node.FlashPayApprovedNode;
import com.hmdp.shared.domain.compensation.node.FlashPayCompletedNode;
import com.hmdp.shared.domain.compensation.node.FlashPayManualReviewNode;
import com.hmdp.shared.domain.compensation.node.FlashPaySubmittedNode;
import com.hmdp.shared.domain.compensation.policy.FlashPayFailurePolicy;
import com.hmdp.shared.stateengine.service.StateMachineDefinition;

import java.util.Collections;

/**
 * 无忧闪赔状态机定义 — 声明闪赔状态机的拓扑结构。
 *
 * <p>定位：无忧闪赔状态机层，是 FlashPayStateCode 的状态机实现。</p>
 *
 * <p>状态流转图：
 * <pre>{@code
 *                     ┌──────────────────┐
 *                     │    SUBMITTED     │ ← 初始状态
 *                     │  (校验申诉原因)   │
 *                     └───┬──────────┬───┘
 *                         │          │
 *                  原因有效 │          │ 原因为空 (failure)
 *                         │          │
 *                         ▼          ▼
 *                  ┌───────────┐ ┌──────────────┐
 *                  │  APPROVED  │ │ MANUAL_REVIEW │ ← 终态
 *                  │ (自动审批)  │ │  (人工审核)    │
 *                  └─────┬─────┘ └──────────────┘
 *                        │
 *                        ▼
 *                  ┌───────────┐
 *                  │ COMPLETED  │ ← 终态
 *                  │ (赔付完成)  │
 *                  └───────────┘
 * }</pre></p>
 *
 * <p>终态：COMPLETED、MANUAL_REVIEW</p>
 *
 * <p>失败策略：{@link FlashPayFailurePolicy} — 始终返回 manualReview，
 * 一旦 SUBMITTED 节点校验失败，直接转人工，不尝试重试。</p>
 */
public class FlashPayStateMachineDefinition extends StateMachineDefinition {

    public FlashPayStateMachineDefinition() {
        super(builder(FlashPayStateCode.MACHINE_CODE, FlashPayStateCode.SUBMITTED.name())
                .addNode(new FlashPaySubmittedNode())
                .addNode(new FlashPayApprovedNode())
                .addNode(new FlashPayCompletedNode())
                .addNode(new FlashPayManualReviewNode())
                .terminalStates(Collections.singleton(FlashPayStateCode.COMPLETED.name()))
                .failurePolicy(new FlashPayFailurePolicy())
                .build());
    }
}
