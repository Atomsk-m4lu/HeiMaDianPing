package com.hmdp.shared.stateengine.model;

/**
 * 状态任务的运行时状态枚举。
 *
 * <p>定位：核心引擎层。区分于 StateTask.currentState（业务状态码，如 CHECKING），
 * runtimeStatus 描述的是任务在引擎中的运行阶段。</p>
 *
 * <p>状态流转：
 * <pre>{@code
 * PENDING → RUNNING → COMPLETED (正常终态)
 *                   → FAILED (等待重试)
 *                   → MANUAL_REVIEW (人工介入)
 * }</pre></p>
 */
public enum StateTaskStatus {
    /** 待处理 — 任务已创建但尚未进入状态机执行 */
    PENDING,
    /** 运行中 — 状态机正在执行该任务 */
    RUNNING,
    /** 失败 — 节点执行失败，等待重试或人工介入 */
    FAILED,
    /** 已完成 — 状态机正常执行完毕，到达终态 */
    COMPLETED,
    /** 人工审核 — 需要人工介入处理 */
    MANUAL_REVIEW
}
