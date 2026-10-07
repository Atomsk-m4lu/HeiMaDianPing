package com.hmdp.shared.stateengine.spi;

import com.hmdp.shared.stateengine.model.StateTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStateTaskRepository implements StateTaskRepository {

    private final Map<String, StateTask> storage = new ConcurrentHashMap<>();

    @Override
    public void save(StateTask task) {
        storage.put(task.getTaskId(), task);
    }

    @Override
    public void update(StateTask task) {
        storage.put(task.getTaskId(), task);
    }

    @Override
    public StateTask findByTaskId(String taskId) {
        return storage.get(taskId);
    }
}
