package com.hmdp.shared.stateengine;

import com.hmdp.shared.stateengine.model.NodeResult;
import com.hmdp.shared.stateengine.model.RetryDecision;
import com.hmdp.shared.stateengine.model.StateTask;
import com.hmdp.shared.stateengine.model.StateTaskStatus;
import com.hmdp.shared.stateengine.policy.FailurePolicy;
import com.hmdp.shared.stateengine.policy.RetryPolicy;
import com.hmdp.shared.stateengine.service.StateContext;
import com.hmdp.shared.stateengine.service.StateEngine;
import com.hmdp.shared.stateengine.service.StateMachineDefinition;
import com.hmdp.shared.stateengine.service.StateMachineRegistry;
import com.hmdp.shared.stateengine.service.StateNode;
import com.hmdp.shared.stateengine.spi.InMemoryStateTaskRepository;
import com.hmdp.shared.stateengine.spi.InMemoryStateTransitionLogRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StateEngineTest {

    @Test
    void should_complete_task_when_all_nodes_succeed() {
        StateMachineRegistry registry = new StateMachineRegistry();
        registry.register(StateMachineDefinition.builder("demo_machine", "SUBMITTED")
                .addNode(new FixedNode("SUBMITTED", NodeResult.next("APPROVED", "submitted ok")))
                .addNode(new FixedNode("APPROVED", NodeResult.next("COMPLETED", "approved ok")))
                .addNode(new FixedNode("COMPLETED", NodeResult.terminalSuccess("completed")))
                .terminalStates(Collections.singleton("COMPLETED"))
                .failurePolicy((task, result) -> RetryDecision.manualReview("terminal failure"))
                .build());
        StateEngine engine = new StateEngine(
                registry,
                new InMemoryStateTaskRepository(),
                new InMemoryStateTransitionLogRepository(),
                new RetryPolicy(3)
        );

        StateTask task = StateTask.create("task-1", "biz-1", "demo_machine", "SUBMITTED", "payload");
        StateTask result = engine.start(task);

        assertEquals(StateTaskStatus.COMPLETED, result.getRuntimeStatus());
        assertEquals("COMPLETED", result.getCurrentState());
        assertEquals(3, result.getTransitionLogs().size());
    }

    @Test
    void should_route_to_manual_review_when_node_fails_and_policy_rejects_retry() {
        StateMachineRegistry registry = new StateMachineRegistry();
        FailurePolicy failurePolicy = (task, result) -> RetryDecision.manualReview(result.getErrorMessage());
        registry.register(StateMachineDefinition.builder("demo_machine", "SUBMITTED")
                .addNode(new FixedNode("SUBMITTED", NodeResult.failure("INVALID_REASON", "reason blank", false)))
                .terminalStates(Collections.singleton("MANUAL_REVIEW"))
                .failurePolicy(failurePolicy)
                .build());
        StateEngine engine = new StateEngine(
                registry,
                new InMemoryStateTaskRepository(),
                new InMemoryStateTransitionLogRepository(),
                new RetryPolicy(3)
        );

        StateTask task = StateTask.create("task-2", "biz-2", "demo_machine", "SUBMITTED", "payload");
        StateTask result = engine.start(task);

        assertEquals(StateTaskStatus.MANUAL_REVIEW, result.getRuntimeStatus());
        assertEquals("MANUAL_REVIEW", result.getCurrentState());
        assertFalse(result.getLastMessage().isEmpty());
    }

    private static final class FixedNode implements StateNode {
        private final String code;
        private final NodeResult result;

        private FixedNode(String code, NodeResult result) {
            this.code = code;
            this.result = result;
        }

        @Override
        public String getStateCode() {
            return code;
        }

        @Override
        public NodeResult execute(StateTask task, StateContext context) {
            return result;
        }
    }
}
