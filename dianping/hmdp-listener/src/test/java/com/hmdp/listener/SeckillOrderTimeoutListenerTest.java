package com.hmdp.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SeckillOrderTimeoutListenerTest {

    @Test
    void shouldInstantiate() {
        assertNotNull(new SeckillOrderTimeoutListener(null));
    }
}
