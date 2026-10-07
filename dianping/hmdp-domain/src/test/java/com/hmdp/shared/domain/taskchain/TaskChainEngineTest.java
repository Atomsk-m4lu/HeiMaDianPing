package com.hmdp.shared.domain.taskchain;

import com.hmdp.shared.domain.taskchain.annotation.TaskNode;
import com.hmdp.shared.domain.taskchain.enums.ExecutionMode;
import com.hmdp.shared.domain.taskchain.enums.ProcessPhase;
import com.hmdp.shared.domain.taskchain.enums.TaskTypeEnum;
import com.hmdp.shared.domain.taskchain.model.PhaseConfig;
import com.hmdp.shared.domain.taskchain.model.TaskContext;
import com.hmdp.shared.domain.taskchain.model.TaskNodeHandler;
import com.hmdp.shared.domain.taskchain.model.TaskNodeHolder;
import com.hmdp.shared.domain.taskchain.model.TaskNodeRegistry;
import com.hmdp.shared.domain.taskchain.service.PhaseExecutionEngine;
import com.hmdp.shared.domain.taskchain.service.TaskChainEngine;
import com.hmdp.shared.domain.taskchain.service.TaskDispatcher;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskChainEngineTest {

    @Test
    void should_execute_parallel_nodes_and_assemble_output() {
        AtomicInteger counter = new AtomicInteger();
        TaskNodeHandler<Long, String> first = context -> {
            context.setAttribute("a", "shop");
            counter.incrementAndGet();
        };
        TaskNodeHandler<Long, String> second = context -> {
            context.setAttribute("b", "blogs");
            counter.incrementAndGet();
        };
        TaskNodeHandler<Long, String> third = context -> {
            String result = context.<String>getAttribute("a") + "-" + context.<String>getAttribute("b");
            context.setOutput(result);
        };

        List<TaskNodeHolder> nodes = Arrays.asList(
                parallel("first", ProcessPhase.DATA_PROCESSING, first),
                parallel("second", ProcessPhase.DATA_PROCESSING, second),
                serial("assemble", ProcessPhase.POST_PROCESSING, third)
        );

        TaskChainEngine engine = createEngine(nodes);
        TaskContext<Long, String> context = new TaskContext<>();
        context.setInput(1L);

        engine.executePhaseChain("shop_detail", context, Arrays.asList(
                new PhaseConfig(ProcessPhase.DATA_PROCESSING, ExecutionMode.PARALLEL),
                new PhaseConfig(ProcessPhase.POST_PROCESSING, ExecutionMode.SERIAL)
        ));

        assertEquals(2, counter.get());
        assertEquals("shop-blogs", context.getOutput());
    }

    private TaskChainEngine createEngine(List<TaskNodeHolder> nodes) {
        TaskNodeRegistry registry = new TaskNodeRegistry(nodes);
        TaskDispatcher dispatcher = new TaskDispatcher(
                Executors.newFixedThreadPool(2),
                Executors.newFixedThreadPool(4)
        );
        PhaseExecutionEngine phaseExecutionEngine = new PhaseExecutionEngine(dispatcher);
        return new TaskChainEngine(registry, phaseExecutionEngine);
    }

    private TaskNodeHolder parallel(String nodeId, ProcessPhase phase, TaskNodeHandler<Long, String> handler) {
        return new TaskNodeHolder(handler, annotation(nodeId, phase, TaskTypeEnum.IO_BOUND));
    }

    private TaskNodeHolder serial(String nodeId, ProcessPhase phase, TaskNodeHandler<Long, String> handler) {
        return new TaskNodeHolder(handler, annotation(nodeId, phase, TaskTypeEnum.CPU_BOUND));
    }

    private TaskNode annotation(String nodeId, ProcessPhase phase, TaskTypeEnum taskTypeEnum) {
        return new TaskNode() {
            @Override
            public String nodeId() {
                return nodeId;
            }

            @Override
            public String nodeName() {
                return nodeId;
            }

            @Override
            public TaskTypeEnum taskType() {
                return taskTypeEnum;
            }

            @Override
            public String group() {
                return "shop_detail";
            }

            @Override
            public int order() {
                return 1;
            }

            @Override
            public boolean skippable() {
                return false;
            }

            @Override
            public String description() {
                return "";
            }

            @Override
            public ProcessPhase phase() {
                return phase;
            }

            @Override
            public long timeout() {
                return 0;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return TaskNode.class;
            }
        };
    }
}
