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

    private final Map<String, List<TaskNodeHolder>> nodeGroups = new LinkedHashMap<>();

    public TaskNodeRegistry(List<TaskNodeHolder> holders) {
        register(holders == null ? Collections.<TaskNodeHolder>emptyList() : holders);
    }

    public TaskNodeRegistry() {
        this(Collections.<TaskNodeHolder>emptyList());
    }

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

    public List<TaskNodeHolder> getNodesByGroup(String group) {
        return nodeGroups.getOrDefault(group, Collections.<TaskNodeHolder>emptyList());
    }

    public Map<ProcessPhase, List<TaskNodeHolder>> getNodesGroupedByPhase(String group) {
        return getNodesByGroup(group).stream().collect(Collectors.groupingBy(
                TaskNodeHolder::getPhase,
                LinkedHashMap::new,
                Collectors.toList()
        ));
    }

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
