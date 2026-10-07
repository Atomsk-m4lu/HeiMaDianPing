package com.hmdp.shared.stateengine.model;

import lombok.Getter;

/**
 * 节点执行结果 — StateNode.execute() 的返回值，封装节点执行后的状态机行为指令。
 *
 * <p>定位：核心引擎层。NodeResult 不描述"节点做了什么业务"，
 * 而是描述"状态机接下来该怎么走"。</p>
 *
 * <p>三个工厂方法对应三种状态机行为：
 * <ul>
 *   <li>{@link #next(String, String)} — <b>流转到下一状态</b>，success=true, terminal=false
 *       <br>引擎收到后：moveTo下一状态，继续while循环</li>
 *   <li>{@link #terminalSuccess(String)} — <b>到达终态</b>，success=true, terminal=true
 *       <br>引擎收到后：markCompleted，退出循环</li>
 *   <li>{@link #failure(String, String, boolean)} — <b>执行失败</b>，success=false
 *       <br>引擎收到后：根据retryable + FailurePolicy + RetryPolicy 决定重试或转人工</li>
 * </ul></p>
 */
@Getter
public class NodeResult {

    /** 节点是否执行成功 */
    private final boolean success;
    /** 是否为终态（成功到达状态机终点） */
    private final boolean terminal;
    /** 失败时是否允许重试 */
    private final boolean retryable;
    /** 下一个状态码（success=true且非终态时有效） */
    private final String nextState;
    /** 失败时的错误码（如 "COMPENSATION_FAILED"、"EMPTY_REASON"） */
    private final String errorCode;
    /** 描述消息，会写入StateTask.lastMessage */
    private final String message;

    private NodeResult(boolean success,
                       boolean terminal,
                       boolean retryable,
                       String nextState,
                       String errorCode,
                       String message) {
        this.success = success;
        this.terminal = terminal;
        this.retryable = retryable;
        this.nextState = nextState;
        this.errorCode = errorCode;
        this.message = message;
    }

    /**
     * 工厂方法 — 流转到下一状态。
     * <p>表示当前节点执行成功，状态机应继续执行 nextState 对应的节点。</p>
     *
     * @param nextState 下一个状态码
     * @param message   流转原因描述
     */
    public static NodeResult next(String nextState, String message) {
        return new NodeResult(true, false, false, nextState, null, message);
    }

    /**
     * 工厂方法 — 到达终态，状态机正常结束。
     * <p>表示当前节点执行成功且不需要继续流转，任务完成。</p>
     *
     * @param message 完成描述
     */
    public static NodeResult terminalSuccess(String message) {
        return new NodeResult(true, true, false, null, null, message);
    }

    /**
     * 工厂方法 — 节点执行失败。
     * <p>引擎收到后进入失败处理路径：根据 retryable、RetryPolicy、FailurePolicy
     * 综合判断是标记 FAILED 等待重试，还是转 MANUAL_REVIEW。</p>
     *
     * @param errorCode 错误码
     * @param message   错误描述
     * @param retryable 是否可重试（true=标记FAILED等重试，false=直接转人工）
     */
    public static NodeResult failure(String errorCode, String message, boolean retryable) {
        return new NodeResult(false, false, retryable, null, errorCode, message);
    }

    /**
     * 兼容旧代码的错误消息获取方法。
     * @deprecated 请直接使用 {@link #getMessage()}
     */
    public String getErrorMessage() {
        return message;
    }
}
