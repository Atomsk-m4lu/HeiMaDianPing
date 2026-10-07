package com.hmdp.shared.domain.taskchain.model;

import com.hmdp.shared.domain.taskchain.annotation.TaskNode;
import com.hmdp.shared.domain.taskchain.enums.ProcessPhase;
import com.hmdp.shared.domain.taskchain.enums.TaskTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务节点持有者
 * <p>
 * 将 {@link TaskNodeHandler} 实例和其 {@link TaskNode} 注解元数据封装在一起，
 * 方便引擎在执行时同时访问处理器和配置信息。
 * </p>
 */
@Getter
@AllArgsConstructor
public class TaskNodeHolder {

    private final TaskNodeHandler<?, ?> handler;
    private final TaskNode annotation;

    public ProcessPhase getPhase() {
        return annotation.phase();
    }

    public long getTimeout() {
        return annotation.timeout();
    }

    public TaskTypeEnum getTaskType() {
        return annotation.taskType();
    }
}
