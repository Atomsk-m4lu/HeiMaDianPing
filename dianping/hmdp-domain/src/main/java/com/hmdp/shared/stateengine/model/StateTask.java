package com.hmdp.shared.stateengine.model;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 状态机任务 — 状态机的核心领域对象，承载任务标识、当前状态、payload 和运行时信息。
 *
 * <p>定位：核心引擎层。StateTask 是状态机流转的最小执行单元，
 * 一条 StateTask 记录对应一次完整的状态机执行过程。</p>
 *
 * <p>生命周期：
 * <pre>{@code
 * create() → PENDING → start() → RUNNING → while循环中流转 → COMPLETED / FAILED / MANUAL_REVIEW
 *                                      ↑
 * restore() ← ← ← ← ← ← ← ← ← ← ← ← ← (从持久层恢复)
 * }</pre></p>
 *
 * <p>两种工厂方法：
 * <ul>
 *   <li>{@link #create(...)} — 创建全新任务，runtimeStatus=PENDING，createdAt=now</li>
 *   <li>{@link #restore(...)} — 从持久层恢复已有任务，保留原始的createdAt和retryCount</li>
 * </ul></p>
 */
@Getter
public class StateTask {

    /** 任务唯一标识 */
    private final String taskId;
    /** 业务键，将状态机任务与业务实体关联（如 order:12345） */
    private final String bizKey;
    /** 所属状态机标识 */
    private final String machineCode;
    /** 序列化后的业务参数，各状态机自行定义格式和解析方式 */
    private final String payload;
    /** 任务创建时间 */
    private final LocalDateTime createdAt;
    /** 流转日志列表，记录每次状态变迁的详细信息 */
    private final List<StateTransitionLog> transitionLogs = new ArrayList<>();
    /** 当前业务状态码（如 CHECKING、COMPENSATING） */
    private String currentState;
    /** 运行时状态（PENDING/RUNNING/FAILED/COMPLETED/MANUAL_REVIEW） */
    private StateTaskStatus runtimeStatus;
    /** 最近一次状态变更的消息描述 */
    private String lastMessage;
    /** 重试次数 */
    private int retryCount;
    /** 最后更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 私有构造器 — 用于创建新任务，初始状态为PENDING。
     */
    private StateTask(String taskId, String bizKey, String machineCode, String currentState, String payload) {
        this.taskId = taskId;
        this.bizKey = bizKey;
        this.machineCode = machineCode;
        this.payload = payload;
        this.currentState = currentState;
        this.runtimeStatus = StateTaskStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        this.lastMessage = "";
    }

    /**
     * 私有构造器 — 用于从持久层恢复任务，保留所有历史字段。
     */
    private StateTask(String taskId, String bizKey, String machineCode, String payload,
                      String currentState, StateTaskStatus runtimeStatus, String lastMessage,
                      int retryCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.taskId = taskId;
        this.bizKey = bizKey;
        this.machineCode = machineCode;
        this.payload = payload;
        this.currentState = currentState;
        this.runtimeStatus = runtimeStatus;
        this.lastMessage = lastMessage;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 工厂方法 — 创建全新任务。
     *
     * @param taskId       任务唯一标识
     * @param bizKey       业务关联键
     * @param machineCode  状态机标识
     * @param currentState 初始业务状态
     * @param payload      序列化后的业务参数
     */
    public static StateTask create(String taskId, String bizKey, String machineCode, String currentState, String payload) {
        return new StateTask(taskId, bizKey, machineCode, currentState, payload);
    }

    /**
     * 工厂方法 — 从持久层恢复任务。
     * <p>与 create() 的区别：保留原始的 createdAt 和 retryCount，
     * 确保重试计数和时间信息不丢失。</p>
     */
    public static StateTask restore(String taskId, String bizKey, String machineCode, String payload,
                                    String currentState, StateTaskStatus runtimeStatus, String lastMessage,
                                    int retryCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new StateTask(taskId, bizKey, machineCode, payload,
                currentState, runtimeStatus, lastMessage, retryCount, createdAt, updatedAt);
    }

    /** 标记任务为运行中 */
    public void markRunning() {
        this.runtimeStatus = StateTaskStatus.RUNNING;
        this.updatedAt = LocalDateTime.now();
    }

    /** 流转到下一个业务状态 */
    public void moveTo(String nextState, String message) {
        this.currentState = nextState;
        this.lastMessage = message;
        this.updatedAt = LocalDateTime.now();
    }

    /** 标记任务为已完成（到达终态） */
    public void markCompleted(String state, String message) {
        this.currentState = state;
        this.runtimeStatus = StateTaskStatus.COMPLETED;
        this.lastMessage = message;
        this.updatedAt = LocalDateTime.now();
    }

    /** 标记任务为失败（等待重试） */
    public void markFailed(String state, String message) {
        this.currentState = state;
        this.runtimeStatus = StateTaskStatus.FAILED;
        this.lastMessage = message;
        this.updatedAt = LocalDateTime.now();
    }

    /** 标记任务为人工审核状态 */
    public void markManualReview(String message) {
        this.currentState = "MANUAL_REVIEW";
        this.runtimeStatus = StateTaskStatus.MANUAL_REVIEW;
        this.lastMessage = message;
        this.updatedAt = LocalDateTime.now();
    }

    /** 重试计数+1 */
    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = LocalDateTime.now();
    }

    /** 追加一条流转日志 */
    public void addTransitionLog(StateTransitionLog transitionLog) {
        this.transitionLogs.add(transitionLog);
    }

    /** 获取流转日志的只读视图 */
    public List<StateTransitionLog> getTransitionLogs() {
        return Collections.unmodifiableList(transitionLogs);
    }
}
