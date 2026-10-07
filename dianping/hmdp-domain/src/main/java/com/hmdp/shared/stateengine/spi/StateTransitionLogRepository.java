package com.hmdp.shared.stateengine.spi;

import com.hmdp.shared.stateengine.model.StateTransitionLog;

public interface StateTransitionLogRepository {

    void save(StateTransitionLog transitionLog);
}
