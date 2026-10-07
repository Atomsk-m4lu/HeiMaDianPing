package com.hmdp.shared.stateengine.service;

import com.hmdp.shared.stateengine.model.NodeResult;
import com.hmdp.shared.stateengine.model.RetryDecision;
import com.hmdp.shared.stateengine.model.StateTask;
import com.hmdp.shared.stateengine.model.StateTransitionLog;
import com.hmdp.shared.stateengine.policy.RetryPolicy;
import com.hmdp.shared.stateengine.spi.StateTaskRepository;
import com.hmdp.shared.stateengine.spi.StateTransitionLogRepository;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 状态机引擎 — 通用状态机运行时，驱动任务在状态节点间流转。
 *
 * <p>定位：核心引擎层，与业务无关。负责执行状态机的 while 循环，
 * 依次调用节点、记录流转日志、处理成功/失败/重试三种路径。</p>
 *
 * <p>两条入口：
 * <ul>
 *   <li>{@link #start(StateTask)} — 新任务首次进入状态机（save）</li>
 *   <li>{@link #resume(StateTask)} — 从FAILED/MANUAL_REVIEW状态恢复执行（update）</li>
 * </ul></p>
 */
public class StateEngine {

    private final StateMachineRegistry registry;
    private final StateTaskRepository taskRepository;
    private final StateTransitionLogRepository logRepository;
    private final RetryPolicy retryPolicy;

    public StateEngine(StateMachineRegistry registry,
                       StateTaskRepository taskRepository,
                       StateTransitionLogRepository logRepository,
                       RetryPolicy retryPolicy) {
        this.registry = registry;
        this.taskRepository = taskRepository;
        this.logRepository = logRepository;
        this.retryPolicy = retryPolicy;
    }

    /**
     * 启动一个新任务进入状态机。
     *
     * @param task 新创建的任务（status=PENDING）
     * @return 执行结束后的任务（可能是COMPLETED/FAILED/MANUAL_REVIEW）
     */
    public StateTask start(StateTask task) {
        task.markRunning();
        taskRepository.save(task);
        return run(task);
    }

    /**
     * 恢复一个已存在的任务继续执行。
     * <p>典型场景：FAILED状态任务经重试策略判断可重试时，
     * 或MANUAL_REVIEW状态任务人工处理后重新驱动。</p>
     *
     * @param task 从持久层恢复的任务
     * @return 执行结束后的任务
     */
    public StateTask resume(StateTask task) {
        task.markRunning();
        taskRepository.update(task);
        return run(task);
    }

    /**
     * 状态机主循环 — 驱动任务在状态节点间流转直到终止或失败。
     *
     * <p>循环内部三条路径：
     * <pre>{@code
     * while (true) {
     *     1. 取当前状态对应的节点并执行
     *     2. 记录流转日志
     *
     *     3. 判断结果：
     *        ├─ 成功 + 终态 → 标记COMPLETED，结束
     *        ├─ 成功 + 非终态 → moveTo下一状态，continue继续循环
     *        └─ 失败 → 根据FailurePolicy + RetryPolicy决定：
     *                  ├─ 可重试 → 标记FAILED（等待外部resume）
     *                  └─ 不可重试 → 标记MANUAL_REVIEW（转人工处理）
     * }</pre></p>
     *
     * <p>每次节点执行都会产生一条StateTransitionLog，记录
     * fromState、toState、耗时、成功/失败等信息，方便审计和排查。</p>
     */
    private StateTask run(StateTask task) {
        // 根据任务的machineCode找到对应的状态机定义
        StateMachineDefinition machine = registry.getRequired(task.getMachineCode());
        while (true) {
            // 1. 获取当前状态对应的节点处理器
            StateNode node = machine.getRequiredNode(task.getCurrentState());
            LocalDateTime startedAt = LocalDateTime.now();

            // 2. 执行节点逻辑
            NodeResult result = node.execute(task, new StateContext(machine.getMachineCode()));

            // 3. 构造并记录流转日志
            StateTransitionLog transitionLog = StateTransitionLog.of(
                    task.getTaskId(),
                    task.getMachineCode(),
                    node.getStateCode(),
                    result.getNextState(),
                    result.isSuccess(),
                    result.getErrorCode(),
                    result.getMessage(),
                    Duration.between(startedAt, LocalDateTime.now()).toMillis()
            );
            task.addTransitionLog(transitionLog);
            logRepository.save(transitionLog);

            // 路径A：节点成功执行且到达终态 → 任务完成
            if (result.isSuccess() && result.isTerminal()) {
                task.markCompleted(node.getStateCode(), result.getMessage());
                taskRepository.update(task);
                return task;
            }

            // 路径B：节点成功执行但未到终态 → 流转到下一状态，继续循环
            if (result.isSuccess()) {
                task.moveTo(result.getNextState(), result.getMessage());
                taskRepository.update(task);
                continue;
            }

            // 路径C：节点执行失败 → 通过FailurePolicy获取重试决策
            RetryDecision decision = machine.getFailurePolicy().onFailure(task, result);
            // 可重试条件：节点标记retryable + 未超最大重试次数 + FailurePolicy未要求转人工
            if (result.isRetryable() && retryPolicy.canRetry(task) && !decision.isManualReview()) {
                task.incrementRetryCount();
                task.markFailed(node.getStateCode(), decision.getMessage());
            } else {
                // 不可重试 → 转人工审核
                task.markManualReview(decision.getMessage());
            }
            taskRepository.update(task);
            return task;
        }
    }
}
