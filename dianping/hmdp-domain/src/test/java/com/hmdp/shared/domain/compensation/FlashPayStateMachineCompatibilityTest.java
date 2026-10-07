package com.hmdp.shared.domain.compensation;

import com.hmdp.shared.domain.compensation.model.FlashPayPayload;
import com.hmdp.shared.domain.compensation.service.FlashPayStateMachineFacade;
import com.hmdp.shared.stateengine.model.StateTask;
import com.hmdp.shared.stateengine.model.StateTaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlashPayStateMachineCompatibilityTest {

    @Test
    void should_complete_flash_compensation_when_reason_is_valid() {
        FlashPayStateMachineFacade facade = FlashPayStateMachineFacade.createDefault();
        StateTask result = facade.start("claim-1", "shop-1",
                new FlashPayPayload("FLASH_PAY", "商家提前打烊，权益未兑现"));

        assertEquals(StateTaskStatus.COMPLETED, result.getRuntimeStatus());
        assertEquals("COMPLETED", result.getCurrentState());
        assertEquals(3, result.getTransitionLogs().size());
        assertTrue(result.getLastMessage().contains("自动赔付"));
    }

    @Test
    void should_route_to_manual_review_when_reason_is_blank() {
        FlashPayStateMachineFacade facade = FlashPayStateMachineFacade.createDefault();
        StateTask result = facade.start("claim-2", "shop-2",
                new FlashPayPayload("FLASH_PAY", ""));

        assertEquals(StateTaskStatus.MANUAL_REVIEW, result.getRuntimeStatus());
        assertEquals("MANUAL_REVIEW", result.getCurrentState());
        assertEquals(1, result.getTransitionLogs().size());
        assertTrue(result.getLastMessage().contains("人工审核"));
    }
}
