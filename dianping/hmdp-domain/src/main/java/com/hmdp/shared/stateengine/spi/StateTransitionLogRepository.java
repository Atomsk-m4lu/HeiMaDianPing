package com.hmdp.shared.stateengine.spi;

import com.hmdp.shared.stateengine.model.StateTransitionLog;

/**
 * 状态流转日志持久化仓库 — SPI 扩展点，定义 StateTransitionLog 的存储契约。
 *
 * <p>定位：核心引擎层。StateEngine 在每次 StateNode.execute() 执行后（无论成功还是失败）
 * 都会调用此接口追加一条流转日志，串联成任务的完整执行轨迹，用于审计、排查和监控。</p>
 *
 * <p>CBB 自带 InMemoryStateTransitionLogRepository 默认实现（ArrayList 内存存储），
 * 业务场景可替换为 DB 实现以持久化审计轨迹。</p>
 */
public interface StateTransitionLogRepository {

    /**
     * 追加一条状态流转日志。
     * <p>StateEngine 在每次节点执行后调用，无论节点成功还是失败都会记录，
     * 确保完整覆盖流转轨迹。</p>
     *
     * @param transitionLog 流转日志，包含 fromState、toState、success、errorCode、durationMs 等信息
     */
    void save(StateTransitionLog transitionLog);
}