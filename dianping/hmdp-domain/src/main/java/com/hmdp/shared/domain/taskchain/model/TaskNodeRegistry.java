package com.hmdp.shared.domain.taskchain.model;

import com.hmdp.shared.domain.taskchain.annotation.TaskNode;
import com.hmdp.shared.domain.taskchain.enums.ProcessPhase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务节点注册表
 * <p>
 * 启动时由 {@link com.hmdp.shared.infrastructure.config.SharedEngineAutoConfiguration} 初始化，
 * 扫描所有 {@link TaskNodeHandler} Bean，按 group 分组、按 order 排序后存储。
 * 引擎执行时通过 getNodesByGroup 或 getNodesGroupedByPhase 获取节点列表。
 * </p>
 */
public class TaskNodeRegistry {

    /**
     * 节点分组存储 (启动时一次性构建好不可变执行计划, 运行时只查表)
     * <p>
     * key   = @TaskNode.group()     (同一 group 的节点一起编排, 比如 "shop_detail" / "shop_rating_refresh")
     * value = 该 group 下的节点列表 (已按 @TaskNode.order() 从小到大排序, 且 Collections.unmodifiableList 包裹, 防止运行时被改)
     * </p>
     * <p>
     * 使用 LinkedHashMap 的原因: 保持 group 的插入顺序 (即 Spring Bean 扫描到的顺序),
     * 避免 HashMap 的不可预测迭代顺序在调试时造成困惑。
     * </p>
     */
    private final Map<String, List<TaskNodeHolder>> nodeGroups = new LinkedHashMap<>();

    /**
     * 带初始节点的构造函数
     * <p>
     * 由 SharedEngineAutoConfiguration 调用: 先 wrapHandlers 把所有 TaskNodeHandler Bean 封装成 TaskNodeHolder,
     * 再把 List<TaskNodeHolder> 传给构造函数 → 内部自动调 register 完成分组 + 排序。
     * </p>
     *
     * @param holders 初始节点列表 (可为 null, 内部做空值保护转 emptyList)
     */
    public TaskNodeRegistry(List<TaskNodeHolder> holders) {
        register(holders == null ? Collections.<TaskNodeHolder>emptyList() : holders);
    }

    /**
     * 空构造函数
     * <p>
     * 由 Spring 通过 SharedEngineAutoConfiguration 注入调用, 内部传空列表 → 后续可通过 register 增量注册。
     * </p>
     */
    public TaskNodeRegistry() {
        this(Collections.<TaskNodeHolder>emptyList());
    }

    /**
     * 注册节点列表 (启动时一次性调用)
     * <p>
     * 两步走:
     * <ol>
     *   <li><b>按 group 分组</b> —— 遍历 holders, 以 @TaskNode.group() 为 key, 放进临时 LinkedHashMap</li>
     *   <li><b>同组内按 order 排序</b> —— Comparator.comparingInt(node -> node.getAnnotation().order()), 升序</li>
     * </ol>
     * 排序完成后用 {@code Collections.unmodifiableList} 包裹再塞进 nodeGroups,
     * 这样运行时外部调用 getNodesByGroup 拿到的列表不可变, 防止意外修改。
     * </p>
     * <p>
     * 示例: 商详链路 6 个节点注册后, nodeGroups 内部结构为:
     * <pre>
     * nodeGroups = {
     *   "shop_detail": [QueryShopBaseNode(order=10), QueryShopBlogsNode(order=20),
     *                   QueryShopReviewsNode(order=30), QueryShopVouchersNode(order=40),
     *                   AssembleShopDetailNode(order=60), EnrichShopDetailNode(order=70)],
     *   "shop_rating_refresh": [...],
     * }
     * </pre>
     * </p>
     *
     * @param holders 要注册的节点列表 (handler + annotation 已封装好)
     */
    public void register(List<TaskNodeHolder> holders) {
        nodeGroups.clear();
        Map<String, List<TaskNodeHolder>> grouped = new LinkedHashMap<>();
        for (TaskNodeHolder holder : holders) {
            grouped.computeIfAbsent(holder.getAnnotation().group(), key -> new ArrayList<>()).add(holder);
        }
        for (Map.Entry<String, List<TaskNodeHolder>> entry : grouped.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(node -> node.getAnnotation().order()));
            nodeGroups.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
    }

    /**
     * 按 group 查询节点列表 (引擎 executePhaseChain 的第一步就是调这个)
     * <p>
     * 由于 register 时已经按 order 排好序, 这里返回的列表天然有序。
     * group 不存在时返回 emptyList 而不是 null, 调用方不用做空判断。
     * </p>
     *
     * @param group 节点分组名 (对应 @TaskNode.group())
     * @return 该 group 下的节点列表 (已按 order 升序, 不可变; 不存在则返回 emptyList)
     */
    public List<TaskNodeHolder> getNodesByGroup(String group) {
        return nodeGroups.getOrDefault(group, Collections.<TaskNodeHolder>emptyList());
    }

    /**
     * 按 group 查询节点, 再按 ProcessPhase 二次分组 (商详链路调的是这个)
     * <p>
     * 从 getNodesByGroup 拿到有序的 group 节点列表后, 用 Stream 再按 @TaskNode.phase() 分组:
     * <pre>
     * getNodesGroupedByPhase("shop_detail") 返回:
     * {
     *   DATA_PROCESSING: [BaseNode(10), BlogsNode(20), ReviewsNode(30), VouchersNode(40)],
     *   POST_PROCESSING: [AssembleNode(60), EnrichNode(70)]
     * }
     * </pre>
     * 使用 LinkedHashMap::new 保持 Phase 的分组插入顺序 (即节点原有的 order 顺序),
     * 这样引擎按 phaseConfigs 遍历时, 节点列表内部的相对顺序不会乱。
     * </p>
     *
     * @param group 节点分组名
     * @return Map<ProcessPhase, 该阶段的节点有序列表>
     */
    public Map<ProcessPhase, List<TaskNodeHolder>> getNodesGroupedByPhase(String group) {
        return getNodesByGroup(group).stream().collect(Collectors.groupingBy(
                TaskNodeHolder::getPhase,
                LinkedHashMap::new,
                Collectors.toList()
        ));
    }

    /**
     * 按 nodeId 查找单个节点 (用于运行时查找某个特定节点, 比如监控上报 / 动态跳过)
     * <p>
     * 线性扫描: 遍历所有 group 的所有节点, 匹配 @TaskNode.nodeId()。
     * 因为节点总数通常不多 (几十到上百), 线性扫描完全够用, 不需要额外维护 nodeId→Holder 的倒排索引。
     * </p>
     *
     * @param nodeId 节点唯一标识 (对应 @TaskNode.nodeId())
     * @return 匹配的 TaskNodeHolder, 没找到返回 null
     */
    public TaskNodeHolder getNodeById(String nodeId) {
        for (List<TaskNodeHolder> holders : nodeGroups.values()) {
            for (TaskNodeHolder holder : holders) {
                if (holder.getAnnotation().nodeId().equals(nodeId)) {
                    return holder;
                }
            }
        }
        return null;
    }

    /**
     * 把 Spring 扫描到的 TaskNodeHandler Bean 列表封装成 TaskNodeHolder 列表
     * <p>
     * SharedEngineAutoConfiguration 里的调用链:
     * <pre>
     * // Spring 自动注入所有 TaskNodeHandler 实现类 (因为都加了 @Component)
     * List<TaskNodeHandler<?, ?>> handlers = ...;
     * registry.register(registry.wrapHandlers(handlers));
     *                   ↑ wrapHandlers 在这里被调用
     * </pre>
     * wrapHandlers 做的事:
     * <ol>
     *   <li>遍历所有 handler Bean</li>
     *   <li>取 handler.getClass().getAnnotation(TaskNode.class) —— 没有 @TaskNode 注解的 handler 跳过</li>
     *   <li>new TaskNodeHolder(handler, annotation) —— 把 Bean + 注解打包在一起</li>
     * </ol>
     * </p>
     *
     * @param handlers Spring 注入的所有 TaskNodeHandler Bean (实现类必须加 @TaskNode 注解)
     * @return 封装好的 TaskNodeHolder 列表, 可直接传给 register()
     */
    public List<TaskNodeHolder> wrapHandlers(List<TaskNodeHandler<?, ?>> handlers) {
        List<TaskNodeHolder> holders = new ArrayList<>();
        for (TaskNodeHandler<?, ?> handler : handlers) {
            TaskNode annotation = handler.getClass().getAnnotation(TaskNode.class);
            if (annotation != null) {
                holders.add(new TaskNodeHolder(handler, annotation));
            }
        }
        return holders;
    }
}