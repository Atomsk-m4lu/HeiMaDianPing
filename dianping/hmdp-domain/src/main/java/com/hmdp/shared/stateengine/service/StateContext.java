package com.hmdp.shared.stateengine.service;

import lombok.Getter;

/**
 * 状态上下文 — 在节点执行时传递状态机级别的元数据。
 *
 * <p>定位：核心引擎层。StateContext 作为 StateNode.execute() 的第二个参数，
 * 提供当前执行环境的上下文信息。当前版本仅包含 machineCode，
 * 预留扩展空间用于后续添加事务上下文、请求级参数等。</p>
 *
 * <p>设计原则：StateContext 是只读的元数据容器，不应在节点执行中被修改。</p>
 */
@Getter
public class StateContext {

    /** 当前状态机标识 */
    private final String machineCode;

    public StateContext(String machineCode) {
        this.machineCode = machineCode;
    }
}
