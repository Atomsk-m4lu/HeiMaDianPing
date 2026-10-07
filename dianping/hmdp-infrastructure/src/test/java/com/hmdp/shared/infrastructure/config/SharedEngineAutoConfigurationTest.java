package com.hmdp.shared.infrastructure.config;

import com.hmdp.shared.domain.compensation.model.FlashPayStateCode;
import com.hmdp.shared.stateengine.service.StateMachineRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharedEngineAutoConfigurationTest {

    @Test
    void stateMachineRegistry_shouldOnlyRegisterFlashPayMachine() {
        SharedEngineAutoConfiguration configuration = new SharedEngineAutoConfiguration();

        StateMachineRegistry registry = configuration.stateMachineRegistry();

        assertDoesNotThrow(() -> registry.getRequired(FlashPayStateCode.MACHINE_CODE));
        assertThrows(IllegalArgumentException.class,
                () -> registry.getRequired("SECKILL_RECONCILIATION"));
    }
}
