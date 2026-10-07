package com.hmdp.shared.domain.taskchain.service;

import com.hmdp.shared.domain.taskchain.enums.ExecutionMode;
import com.hmdp.shared.domain.taskchain.model.PhaseConfig;
import com.hmdp.shared.domain.taskchain.model.PhaseExecutionResult;
import com.hmdp.shared.domain.taskchain.model.TaskContext;
import com.hmdp.shared.domain.taskchain.model.TaskNodeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 阶段执行引擎
 * <p>
 * 负责执行单个阶段内的所有节点，根据 {@link PhaseConfig#getExecutionMode()} 决定串行或并行：
 * <ul>
 *   <li>串行：按 order 顺序依次调用 {@link TaskDispatcher#dispatchNodeSync}</li>
 *   <li>并行：为每个节点提交异步任务，通过 CompletableFuture.allOf 等待全部完成，
 *       再将各节点的 context 副本合并回主 context</li>
 * </ul>
 * </p>
 */
public class PhaseExecutionEngine {

    private final TaskDispatcher taskDispatcher;

    public PhaseExecutionEngine(TaskDispatcher taskDispatcher) {
        this.taskDispatcher = taskDispatcher;
    }

    public <I, O> PhaseExecutionResult executePhase(PhaseConfig phaseConfig, List<TaskNodeHolder> nodes, TaskContext<I, O> context) {
        long startTime = System.currentTimeMillis();
        try {
            if (nodes == null || nodes.isEmpty()) {
                return PhaseExecutionResult.success(phaseConfig.getPhase(), startTime, System.currentTimeMillis());
            }
            if (phaseConfig.getExecutionMode() == ExecutionMode.PARALLEL) {
                executeParallel(phaseConfig, nodes, context);
            } else {
                executeSerial(phaseConfig, nodes, context);
            }
            if (!context.isSuccess() && phaseConfig.isFailOnError()) {
                return PhaseExecutionResult.fail(phaseConfig.getPhase(), startTime, System.currentTimeMillis(), context.getErrorMessage(), context.getException());
            }
            return PhaseExecutionResult.success(phaseConfig.getPhase(), startTime, System.currentTimeMillis());
        } catch (Exception e) {
            context.markFailed("Phase execution failed: " + phaseConfig.getPhase().getDesc(), e);
            return PhaseExecutionResult.fail(phaseConfig.getPhase(), startTime, System.currentTimeMillis(), e.getMessage(), e);
        }
    }

    private <I, O> void executeSerial(PhaseConfig phaseConfig, List<TaskNodeHolder> nodes, TaskContext<I, O> context) {
        for (TaskNodeHolder node : nodes) {
            if (context.isTerminateChain()) {
                break;
            }
            context.setSkipCurrentNode(false);
            taskDispatcher.dispatchNodeSync(node, context);
            if (!context.isSuccess() && phaseConfig.isFailOnError() && !node.getAnnotation().skippable()) {
                context.markTerminate();
                break;
            }
        }
    }

    private <I, O> void executeParallel(PhaseConfig phaseConfig, List<TaskNodeHolder> nodes, TaskContext<I, O> context) throws Exception {
        List<CompletableFuture<TaskContext<I, O>>> futures = new ArrayList<>(nodes.size());
        for (TaskNodeHolder node : nodes) {
            futures.add(taskDispatcher.dispatchNodeAsync(node, context.copy()));
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        waitForPhase(phaseConfig, all);

        for (CompletableFuture<TaskContext<I, O>> future : futures) {
            TaskContext<I, O> nodeContext = future.get();
            context.mergeError(nodeContext);
            context.mergeData(nodeContext);
        }
    }

    private void waitForPhase(PhaseConfig phaseConfig, CompletableFuture<Void> all) throws Exception {
        if (phaseConfig.getTimeout() <= 0L) {
            all.get();
            return;
        }
        try {
            all.get(phaseConfig.getTimeout(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Phase timed out: " + phaseConfig.getPhase().getDesc(), e);
        }
    }
}
