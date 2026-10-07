package com.hmdp.shared.domain.taskchain.service;

import com.hmdp.shared.domain.taskchain.enums.ExecutionMode;
import com.hmdp.shared.domain.taskchain.enums.ProcessPhase;
import com.hmdp.shared.domain.taskchain.model.PhaseConfig;
import com.hmdp.shared.domain.taskchain.model.TaskContext;
import com.hmdp.shared.domain.taskchain.model.TaskNodeRegistry;
import com.hmdp.shared.domain.taskchain.model.TaskNodeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 任务链引擎
 * <p>
 * 负责按阶段编排和执行任务节点，支持两种入口：
 * <ul>
 *   <li>executeChain：使用默认阶段配置（全部串行）执行指定 group 的所有节点</li>
 *   <li>executePhaseChain：使用自定义阶段配置，可以为不同阶段指定串行或并行模式</li>
 * </ul>
 * 商详链路使用 executePhaseChain，DATA_PROCESSING 阶段并行，POST_PROCESSING 阶段串行。
 * </p>
 */
public class TaskChainEngine {

    private final TaskNodeRegistry taskNodeRegistry;
    private final PhaseExecutionEngine phaseExecutionEngine;

    public TaskChainEngine(TaskNodeRegistry taskNodeRegistry, PhaseExecutionEngine phaseExecutionEngine) {
        this.taskNodeRegistry = taskNodeRegistry;
        this.phaseExecutionEngine = phaseExecutionEngine;
    }

    public <I, O> TaskContext<I, O> executeChain(String group, TaskContext<I, O> context) {
        return executeChain(taskNodeRegistry.getNodesByGroup(group), context);
    }

    public <I, O> TaskContext<I, O> executeChain(List<TaskNodeHolder> nodes, TaskContext<I, O> context) {
        List<PhaseConfig> defaultPhases = new ArrayList<>();
        defaultPhases.add(new PhaseConfig(ProcessPhase.PRE_VALIDATION, ExecutionMode.SERIAL));
        defaultPhases.add(new PhaseConfig(ProcessPhase.PRE_PROCESSING, ExecutionMode.SERIAL));
        defaultPhases.add(new PhaseConfig(ProcessPhase.DATA_PROCESSING, ExecutionMode.SERIAL));
        defaultPhases.add(new PhaseConfig(ProcessPhase.DATA_INSERTION, ExecutionMode.SERIAL));
        defaultPhases.add(new PhaseConfig(ProcessPhase.POST_PROCESSING, ExecutionMode.SERIAL));
        return executePhaseChain(nodes, context, defaultPhases);
    }

    public <I, O> TaskContext<I, O> executePhaseChain(String group, TaskContext<I, O> context, List<PhaseConfig> phaseConfigs) {
        Map<ProcessPhase, List<TaskNodeHolder>> phaseNodesMap = taskNodeRegistry.getNodesGroupedByPhase(group);
        return executePhaseChain(phaseNodesMap, context, phaseConfigs);
    }

    public <I, O> TaskContext<I, O> executePhaseChain(List<TaskNodeHolder> nodes, TaskContext<I, O> context, List<PhaseConfig> phaseConfigs) {
        Map<ProcessPhase, List<TaskNodeHolder>> phaseNodesMap = new java.util.LinkedHashMap<>();
        for (TaskNodeHolder node : nodes) {
            phaseNodesMap.computeIfAbsent(node.getPhase(), key -> new ArrayList<>()).add(node);
        }
        return executePhaseChain(phaseNodesMap, context, phaseConfigs);
    }

    private <I, O> TaskContext<I, O> executePhaseChain(Map<ProcessPhase, List<TaskNodeHolder>> phaseNodesMap, TaskContext<I, O> context, List<PhaseConfig> phaseConfigs) {
        for (PhaseConfig phaseConfig : phaseConfigs) {
            if (context.isTerminateChain()) {
                break;
            }
            List<TaskNodeHolder> phaseNodes = phaseNodesMap.get(phaseConfig.getPhase());
            phaseExecutionEngine.executePhase(phaseConfig, phaseNodes, context);
        }
        return context;
    }
}
