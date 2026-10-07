package com.hmdp.shared.domain.taskchain.service;

import com.hmdp.shared.domain.taskchain.enums.TaskTypeEnum;
import com.hmdp.shared.domain.taskchain.model.TaskContext;
import com.hmdp.shared.domain.taskchain.model.TaskNodeHandler;
import com.hmdp.shared.domain.taskchain.model.TaskNodeHolder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * 任务分发器
 * <p>
 * 根据节点的 {@link TaskTypeEnum} 选择对应的线程池，将节点提交异步执行：
 * <ul>
 *   <li>IO_BOUND → ioExecutor（线程数较多，适合等待数据库/缓存/RPC）</li>
 *   <li>CPU_BOUND → cpuExecutor（线程数 = CPU 核数，避免过多上下文切换）</li>
 * </ul>
 * dispatchNodeSync 是同步版本（内部等待 Future 完成），用于串行阶段；
 * dispatchNodeAsync 是异步版本，返回 CompletableFuture，用于并行阶段。
 * </p>
 */
public class TaskDispatcher {

    private final Executor cpuBoundExecutor;
    private final Executor ioBoundExecutor;

    public TaskDispatcher(Executor cpuBoundExecutor, Executor ioBoundExecutor) {
        this.cpuBoundExecutor = cpuBoundExecutor;
        this.ioBoundExecutor = ioBoundExecutor;
    }

    public Executor getExecutor(TaskTypeEnum taskType) {
        if (taskType == TaskTypeEnum.CPU_BOUND) {
            return cpuBoundExecutor;
        }
        return ioBoundExecutor;
    }

    public <I, O> void dispatchNodeSync(TaskNodeHolder holder, TaskContext<I, O> context) {
        try {
            dispatchNodeAsync(holder, context).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            context.markFailed("Node interrupted: " + holder.getAnnotation().nodeId(), e);
        } catch (ExecutionException e) {
            context.markFailed("Node execution failed: " + holder.getAnnotation().nodeId(), e.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    public <I, O> CompletableFuture<TaskContext<I, O>> dispatchNodeAsync(TaskNodeHolder holder, TaskContext<I, O> context) {
        Executor executor = getExecutor(holder.getTaskType());
        return CompletableFuture.supplyAsync(() -> {
            try {
                context.setCurrentNodeId(holder.getAnnotation().nodeId());
                ((TaskNodeHandler<I, O>) holder.getHandler()).execute(context);
                return context;
            } catch (Throwable throwable) {
                context.markFailed("Node execution failed: " + holder.getAnnotation().nodeId(), throwable);
                if (!holder.getAnnotation().skippable()) {
                    context.markTerminate();
                }
                return context;
            }
        }, executor);
    }
}
