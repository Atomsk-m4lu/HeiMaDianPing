package com.hmdp.shared.stateengine.spi;

import com.hmdp.shared.stateengine.model.StateTransitionLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryStateTransitionLogRepository implements StateTransitionLogRepository {

    private final List<StateTransitionLog> storage = new ArrayList<>();

    @Override
    public void save(StateTransitionLog transitionLog) {
        storage.add(transitionLog);
    }

    public List<StateTransitionLog> getStorage() {
        return Collections.unmodifiableList(storage);
    }
}
