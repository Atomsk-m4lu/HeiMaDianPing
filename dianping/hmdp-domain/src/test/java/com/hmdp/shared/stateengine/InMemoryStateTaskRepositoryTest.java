package com.hmdp.shared.stateengine;

import com.hmdp.shared.stateengine.model.StateTask;
import com.hmdp.shared.stateengine.spi.InMemoryStateTaskRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InMemoryStateTaskRepositoryTest {

    @Test
    void findByTaskId_shouldReturnSavedTask() {
        InMemoryStateTaskRepository repository = new InMemoryStateTaskRepository();
        StateTask task = StateTask.create("task-1", "biz-1", "MACHINE", "CHECKING", "payload");

        repository.save(task);

        StateTask restored = repository.findByTaskId("task-1");

        assertNotNull(restored);
        assertEquals("task-1", restored.getTaskId());
        assertEquals("CHECKING", restored.getCurrentState());
    }
}
