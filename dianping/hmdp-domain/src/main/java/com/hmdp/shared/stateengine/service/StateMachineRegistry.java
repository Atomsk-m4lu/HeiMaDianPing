package com.hmdp.shared.stateengine.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 状态机注册表 — 管理所有已注册的 StateMachineDefinition。
 *
 * <p>定位：核心引擎层。启动时，所有状态机定义通过 {@link #register(StateMachineDefinition)}
 * 注册到此表，StateEngine 运行时通过 machineCode 查找对应的状态机定义。</p>
 *
 * <p>线程安全：使用 ConcurrentHashMap 保证并发注册和读取的安全性。</p>
 */
public class StateMachineRegistry {

    /** machineCode → StateMachineDefinition 的映射 */
    private final Map<String, StateMachineDefinition> definitions = new ConcurrentHashMap<>();

    /**
     * 注册一个状态机定义。
     * <p>如果同一 machineCode 重复注册，后注册的会覆盖先注册的。</p>
     */
    public void register(StateMachineDefinition definition) {
        definitions.put(definition.getMachineCode(), definition);
    }

    /**
     * 根据 machineCode 获取状态机定义，未找到则抛异常。
     *
     * @throws IllegalArgumentException 如果没有注册对应 machineCode 的状态机
     */
    public StateMachineDefinition getRequired(String machineCode) {
        StateMachineDefinition definition = definitions.get(machineCode);
        if (definition == null) {
            throw new IllegalArgumentException("No state machine registered for code: " + machineCode);
        }
        return definition;
    }
}
