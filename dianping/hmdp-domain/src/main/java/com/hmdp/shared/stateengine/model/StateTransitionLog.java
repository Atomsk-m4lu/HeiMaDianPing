package com.hmdp.shared.stateengine.model;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 状态流转日志 — 记录状态机中每一次状态变迁的详细信息。
 *
 * <p>定位：核心引擎层。每次 StateNode.execute() 执行后，
 * StateEngine 都会生成一条 StateTransitionLog，无论成功还是失败。
 * 这些日志串联起来就是任务的完整执行轨迹，用于审计、排查和监控。</p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>fromState / toState — 状态变迁的起点和终点</li>
 *   <li>success — 本次节点执行是否成功</li>
 *   <li>errorCode — 失败时的错误码</li>
 *   <li>durationMs — 节点执行耗时（毫秒）</li>
 *   <li>createdAt — 日志创建时间</li>
 * </ul></p>
 */
@Getter
public class StateTransitionLog {

    private final String taskId;
    private final String machineCode;
    /** 变迁前的状态码 */
    private final String fromState;
    /** 变迁后的状态码 */
    private final String toState;
    /** 节点执行是否成功 */
    private final boolean success;
    /** 错误码（成功时为null） */
    private final String errorCode;
    /** 消息描述 */
    private final String message;
    /** 节点执行耗时（毫秒） */
    private final long durationMs;
    /** 日志创建时间 */
    private final LocalDateTime createdAt;

    private StateTransitionLog(String taskId,
                               String machineCode,
                               String fromState,
                               String toState,
                               boolean success,
                               String errorCode,
                               String message,
                               long durationMs) {
        this.taskId = taskId;
        this.machineCode = machineCode;
        this.fromState = fromState;
        this.toState = toState;
        this.success = success;
        this.errorCode = errorCode;
        this.message = message;
        this.durationMs = durationMs;
        this.createdAt = LocalDateTime.now();
    }

    /** 工厂方法 — 创建一条流转日志 */
    public static StateTransitionLog of(String taskId,
                                        String machineCode,
                                        String fromState,
                                        String toState,
                                        boolean success,
                                        String errorCode,
                                        String message,
                                        long durationMs) {
        return new StateTransitionLog(taskId, machineCode, fromState, toState, success, errorCode, message, durationMs);
    }
}
