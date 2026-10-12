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

    /** 状态机注册表，运行时根据 task.machineCode 查找对应的 StateMachineDefinition */
    private final StateMachineRegistry registry;
    /** 任务持久化仓库，负责 save（start时）/ update（每次节点执行后）/ findByTaskId（resume时） */
    private final StateTaskRepository taskRepository;
    /** 流转日志持久化仓库，每次 StateNode.execute() 执行后追加一条 StateTransitionLog */
    private final StateTransitionLogRepository logRepository;
    /** 重试策略，失败路径中通过 canRetry(task) 判断是否还能重试（与 FailurePolicy 联合决策） */
    private final RetryPolicy retryPolicy;

    /**
     * 构造函数注入四个必选依赖。
     * <p>StateEngine 是与业务无关的通用引擎，所有依赖通过构造函数传入以便测试和灵活组装：
     * <ul>
     *   <li>registry — 注册了所有 StateMachineDefinition，引擎运行时查表驱动</li>
     *   <li>taskRepository / logRepository — SPI 扩展点，生产环境可换 DB 实现，测试用 InMemory</li>
     *   <li>retryPolicy — 控制最大重试次数，FailurePolicy 控制"是否建议重试"，二者联合决策</li>
     * </ul></p>
     *
     * @param registry       状态机注册表，启动时需先注册所有 StateMachineDefinition
     * @param taskRepository 任务持久化仓库，CBB 默认提供 InMemoryStateTaskRepository
     * @param logRepository  流转日志持久化仓库，CBB 默认提供 InMemoryStateTransitionLogRepository
     * @param retryPolicy    重试次数策略，构造时指定 maxRetryCount，如 {@code new RetryPolicy(3)}
     */
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