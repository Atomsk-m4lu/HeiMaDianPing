package com.hmdp.shared.stateengine.service;

import com.hmdp.shared.stateengine.policy.FailurePolicy;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 状态机定义 — 声明式描述一个状态机的完整拓扑结构。
 *
 * <p>定位：核心引擎层。通过 Builder 模式组装状态机四大要素：
 * <ul>
 *   <li><b>节点集合</b>（nodes）— 每个状态对应的处理节点（StateNode）</li>
 *   <li><b>初始状态</b>（initialState）— 任务进入状态机时的起始状态</li>
 *   <li><b>终态集合</b>（terminalStates）— 状态机可以正常结束的状态</li>
 *   <li><b>失败策略</b>（failurePolicy）— 节点执行失败时的处理决策</li>
 * </ul></p>
 *
 * <p>状态流转并不显式定义在 Definition 中，而是由各 StateNode.execute()
 * 返回的 NodeResult 动态决定下一状态。这种设计使状态流转逻辑
 * 内聚在节点内部，而非集中声明。</p>
 *
 * <p>使用示例：
 * <pre>{@code
 * StateMachineDefinition machine = StateMachineDefinition
 *     .builder("ORDER", "CREATED")
 *     .addNode(new OrderCreatedNode())
 *     .addNode(new OrderPaidNode())
 *     .addNode(new OrderCompletedNode())
 *     .terminalStates(Set.of("COMPLETED"))
 *     .failurePolicy(new OrderFailurePolicy())
 *     .build();
 * }</pre></p>
 */
public class StateMachineDefinition {

    /** 状态机唯一标识，如 "SECKILL_RECONCILIATION"、"FLASH_PAY" */
    private final String machineCode;
    /** 初始状态码，任务进入状态机时的起始状态 */
    private final String initialState;
    /** 状态码 → 节点处理器的映射，使用LinkedHashMap保持插入顺序 */
    private final Map<String, StateNode> nodes;
    /** 终态状态码集合，到达这些状态视为状态机正常结束 */
    private final Set<String> terminalStates;
    /** 节点执行失败时的处理策略 */
    private final FailurePolicy failurePolicy;

    protected StateMachineDefinition(String machineCode,
                                     String initialState,
                                     Map<String, StateNode> nodes,
                                     Set<String> terminalStates,
                                     FailurePolicy failurePolicy) {
        this.machineCode = machineCode;
        this.initialState = initialState;
        this.nodes = nodes;
        this.terminalStates = terminalStates;
        this.failurePolicy = failurePolicy;
    }

    /**
     * 拷贝构造函数 — 供子类通过 super(definition) 扩展状态机定义。
     * <p>子类（如业务状态机定义类）继承 StateMachineDefinition 后，
     * 在构造函数中通过 Builder 构建定义，再通过 super 传递。</p>
     */
    protected StateMachineDefinition(StateMachineDefinition definition) {
        this(definition.machineCode, definition.initialState, definition.nodes, definition.terminalStates, definition.failurePolicy);
    }

    /**
     * 创建 Builder 实例。
     *
     * @param machineCode  状态机唯一标识
     * @param initialState 初始状态码
     */
    public static Builder builder(String machineCode, String initialState) {
        return new Builder(machineCode, initialState);
    }

    public String getMachineCode() {
        return machineCode;
    }

    public String getInitialState() {
        return initialState;
    }

    public FailurePolicy getFailurePolicy() {
        return failurePolicy;
    }

    /** 判断给定状态是否为终态 */
    public boolean isTerminal(String state) {
        return terminalStates.contains(state);
    }

    /**
     * 获取指定状态对应的节点处理器，如果未注册则抛出异常。
     *
     * @throws IllegalArgumentException 如果状态码未注册对应节点
     */
    public StateNode getRequiredNode(String stateCode) {
        StateNode stateNode = nodes.get(stateCode);
        if (stateNode == null) {
            throw new IllegalArgumentException("No state node registered for state: " + stateCode);
        }
        return stateNode;
    }

    /** 获取所有节点的只读视图 */
    public Collection<StateNode> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * Builder — 流式构建 StateMachineDefinition。
     *
     * <p>通过链式调用 addNode()、terminalStates()、failurePolicy()
     * 逐步组装，最后调用 build() 生成不可变的 StateMachineDefinition。</p>
     */
    public static class Builder {
        private final String machineCode;
        private final String initialState;
        /** 使用LinkedHashMap保持节点注册顺序 */
        private final Map<String, StateNode> nodes = new LinkedHashMap<>();
        private Set<String> terminalStates = Collections.emptySet();
        private FailurePolicy failurePolicy;

        private Builder(String machineCode, String initialState) {
            this.machineCode = machineCode;
            this.initialState = initialState;
        }

        /** 注册一个状态节点 */
        public Builder addNode(StateNode node) {
            this.nodes.put(node.getStateCode(), node);
            return this;
        }

        /** 设置终态集合 */
        public Builder terminalStates(Set<String> terminalStates) {
            this.terminalStates = terminalStates;
            return this;
        }

        /** 设置失败策略 */
        public Builder failurePolicy(FailurePolicy failurePolicy) {
            this.failurePolicy = failurePolicy;
            return this;
        }

        /** 构建不可变的 StateMachineDefinition */
        public StateMachineDefinition build() {
            return new StateMachineDefinition(machineCode, initialState, new LinkedHashMap<>(nodes), terminalStates, failurePolicy);
        }
    }
}
