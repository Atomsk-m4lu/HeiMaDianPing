package com.hmdp.shared.stateengine.spi;

import com.hmdp.shared.stateengine.model.StateTask;

public interface StateTaskRepository {

    void save(StateTask task);

    void update(StateTask task);

    StateTask findByTaskId(String taskId);
}
