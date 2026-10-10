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

    /** 任务节点注册表: 启动时扫描所有 @TaskNode Bean, 按 group 分组 + order 排序后存储 */
    private final TaskNodeRegistry taskNodeRegistry;

    /** 阶段执行引擎: 负责单个 Phase 内部的节点调度 (串行/并行) + 异常处理 + 线程池路由 */
    private final PhaseExecutionEngine phaseExecutionEngine;

    /**
     * 构造函数注入两个必选依赖
     * <p>
     * TaskChainEngine 是"薄编排层", 自己不做重活:
     * <ul>
     *   <li>TaskNodeRegistry — 启动时一次性构建好不可变执行计划, 引擎运行时只查表</li>
     *   <li>PhaseExecutionEngine — 真正干活的引擎, 引擎只负责把 PhaseConfig 和节点列表塞给它</li>
     * </ul>
     * </p>
     */
    public TaskChainEngine(TaskNodeRegistry taskNodeRegistry, PhaseExecutionEngine phaseExecutionEngine) {
        this.taskNodeRegistry = taskNodeRegistry;
        this.phaseExecutionEngine = phaseExecutionEngine;
    }

    // ========================================================================
    // 公开入口 1: executeChain —— 使用默认全串行策略
    //   适用场景: 写入类链路 / 节点间有数据依赖 / 不需要并行提速的场景
    //   商详链路不用这个入口, 因为它需要 DATA_PROCESSING 并行
    // ========================================================================

    /**
     * 执行指定 group 的整条任务链 (默认 5 阶段全串行)
     * <p>
     * 便捷入口, 调用方只给 group 字符串, 引擎自动:
     * <ol>
     *   <li>从 Registry 查出来该 group 的所有节点</li>
     *   <li>自动生成 PRE_VALIDATION → PRE_PROCESSING → DATA_PROCESSING → DATA_INSERTION → POST_PROCESSING 5 个阶段, 全部配 SERIAL</li>
     *   <li>按默认策略逐阶段执行</li>
     * </ol>
     * </p>
     *
     * @param group   节点分组名, 对应 @TaskNode.group()
     * @param context 贯穿全链的数据容器 (输入 / 输出 / 节点间共享 attributes)
     * @param <I>     输入类型
     * @param <O>     输出类型
     * @return 执行完后的 context (output 里就是最终结果)
     */
    public <I, O> TaskContext<I, O> executeChain(String group, TaskContext<I, O> context) {
        return executeChain(taskNodeRegistry.getNodesByGroup(group), context);
    }

    /**
     * 手动传入节点列表, 使用默认全串行策略执行
     * <p>
     * 内部自动构建 5 个阶段的 PhaseConfig 列表, 全部 ExecutionMode.SERIAL, 然后委托给 executePhaseChain 私有核心方法。
     * </p>
     *
     * @param nodes   要执行的节点列表 (通常来自 Registry.getNodesByGroup, 也可以手动构造)
     * @param context 任务上下文
     */
    public <I, O> TaskContext<I, O> executeChain(List<TaskNodeHolder> nodes, TaskContext<I, O> context) {
        List<PhaseConfig> defaultPhases = new ArrayList<>();
        defaultPhases.add(new PhaseConfig(ProcessPhase.PRE_VALIDATION, ExecutionMode.SERIAL));
        defaultPhases.add(new PhaseConfig(ProcessPhase.PRE_PROCESSING, ExecutionMode.SERIAL));
        defaultPhases.add(new PhaseConfig(ProcessPhase.DATA_PROCESSING, ExecutionMode.SERIAL));
        defaultPhases.add(new PhaseConfig(ProcessPhase.DATA_INSERTION, ExecutionMode.SERIAL));
        defaultPhases.add(new PhaseConfig(ProcessPhase.POST_PROCESSING, ExecutionMode.SERIAL));
        return executePhaseChain(nodes, context, defaultPhases);
    }

    // ========================================================================
    // 公开入口 2: executePhaseChain —— 使用调用方自定义的阶段策略
    //   适用场景: 商详链路 (DATA_PROCESSING 并行 + POST_PROCESSING 串行)
    //            调用方决定要哪些阶段、每个阶段串行还是并行
    // ========================================================================

    /**
     * 执行指定 group 的任务链, 使用调用方自定义的 PhaseConfig 列表
     * <p>
     * 商详链路就是这样调的:
     * <pre>
     * taskChainEngine.executePhaseChain("shop_detail", context, Arrays.asList(
     *     new PhaseConfig(DATA_PROCESSING, PARALLEL),    // 4 个查询节点并行跑
     *     new PhaseConfig(POST_PROCESSING, SERIAL)       // Assemble + Enrich 串行
     * ));
     * </pre>
     * 引擎先从 Registry 查该 group 的节点并按 Phase 分组, 然后连同调用方的 phaseConfigs 一起交给私有核心方法。
     * 注意: phaseConfigs 里没配的阶段会被跳过 (Registry 里该阶段没有节点, 或引擎循环里拿不到就直接忽略)。
     * </p>
     *
     * @param group        节点分组名
     * @param context      任务上下文
     * @param phaseConfigs 调用方自定义的阶段配置列表 (顺序决定了执行顺序)
     */
    public <I, O> TaskContext<I, O> executePhaseChain(String group, TaskContext<I, O> context, List<PhaseConfig> phaseConfigs) {
        Map<ProcessPhase, List<TaskNodeHolder>> phaseNodesMap = taskNodeRegistry.getNodesGroupedByPhase(group);
        return executePhaseChain(phaseNodesMap, context, phaseConfigs);
    }

    /**
     * 手动传入节点列表 + 自定义阶段策略
     * <p>
     * 引擎内部先把节点列表按各自 @TaskNode.phase() 分组, 然后交给私有核心方法。
     * 适用于不通过 Registry、手动构造节点列表的场景 (比如单元测试)。
     * </p>
     *
     * @param nodes        手动构造的节点列表
     * @param context      任务上下文
     * @param phaseConfigs 自定义阶段配置
     */
    public <I, O> TaskContext<I, O> executePhaseChain(List<TaskNodeHolder> nodes, TaskContext<I, O> context, List<PhaseConfig> phaseConfigs) {
        Map<ProcessPhase, List<TaskNodeHolder>> phaseNodesMap = new java.util.LinkedHashMap<>();
        for (TaskNodeHolder node : nodes) {
            phaseNodesMap.computeIfAbsent(node.getPhase(), key -> new ArrayList<>()).add(node);
        }
        return executePhaseChain(phaseNodesMap, context, phaseConfigs);
    }

    // ========================================================================
    // 私有核心方法: 所有公开入口最终汇聚到这里
    //   职责 = 遍历 phaseConfigs → 检查 terminateChain → 委托 PhaseEngine
    // ========================================================================

    /**
     * 任务链引擎的核心驱动循环
     * <p>
     * 所有公开入口 (executeChain / executePhaseChain 的两个重载) 最终都把数据准备好后交给这里。
     * 3 行核心逻辑:
     * <ol>
     *   <li>遍历 phaseConfigs (注意顺序是调用方传入的顺序, 不是 ProcessPhase 的 order)</li>
     *   <li>每次循环开头检查 context.terminateChain —— 某个不可跳过的节点失败时, 引擎立即 break, 后续 Phase 不再执行</li>
     *   <li>委托给 PhaseExecutionEngine.executePhase, PhaseEngine 根据 ExecutionMode 决定串行还是并行跑该阶段的节点</li>
     * </ol>
     * </p>
     *
     * @param phaseNodesMap  节点按 Phase 分组后的 Map (key=ProcessPhase, value=该阶段的 TaskNodeHolder 列表)
     * @param context        贯穿全链的任务上下文
     * @param phaseConfigs   调用方传入的阶段配置 (顺序决定了 Phase 的执行顺序)
     */
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