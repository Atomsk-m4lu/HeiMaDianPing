package com.hmdp.shared.stateengine.service;

import com.hmdp.shared.stateengine.model.NodeResult;
import com.hmdp.shared.stateengine.model.StateTask;

/**
 * 状态节点接口 — 状态机中每个状态的业务处理单元。
 *
 * <p>定位：核心引擎层。每个实现了 StateNode 的类代表状态机图中的一个顶点，
 * 其 execute() 方法封装了该状态的业务逻辑，并通过返回值 NodeResult
 * 告诉引擎下一步应该流转到哪个状态。</p>
 *
 * <p>实现要点：
 * <ul>
 *   <li>{@link #getStateCode()} — 返回节点对应的状态码，必须唯一</li>
 *   <li>{@link #execute(StateTask, StateContext)} — 执行当前状态的业务逻辑，
 *       返回 NodeResult 决定状态机的下一步行为</li>
 * </ul></p>
 */
public interface StateNode {

    /**
     * 返回当前节点对应的状态码。
     * <p>该值既作为注册到 StateMachineDefinition 的 key，
     * 也作为执行时匹配 StateTask.currentState 的标识。</p>
     */
    String getStateCode();

    /**
     * 执行当前状态的业务逻辑。
     *
     * <p>返回值决定状态机行为：
     * <ul>
     *   <li>{@link NodeResult#next(String, String)} — 流转到下一状态</li>
     *   <li>{@link NodeResult#terminalSuccess(String)} — 到达终态，结束</li>
     *   <li>{@link NodeResult#failure(String, String, boolean)} — 执行失败</li>
     * </ul></p>
     *
     * @param task    当前任务对象，包含payload和运行时信息
     * @param context 状态上下文，包含machineCode等元数据
     * @return 节点执行结果，决定状态机下一步行为
     */
    NodeResult execute(StateTask task, StateContext context);
}
