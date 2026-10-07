package com.hmdp.shared.domain.taskchain.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务链上下文
 * <p>
 * 贯穿整条任务链的数据载体，各节点通过它共享中间数据：
 * <ul>
 *   <li>input/output：链路的输入和最终输出</li>
 *   <li>attributes：节点间传递中间数据的 KV 存储（线程安全）</li>
 *   <li>success/errorMessage/exception：执行状态，节点失败时通过 markFailed 写入</li>
 *   <li>terminateChain：设为 true 后引擎会跳过后续所有节点</li>
 * </ul>
 * 并行执行时每个节点拿到的是 copy()，执行完后通过 mergeError/mergeData 合并回主 context。
 * </p>
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 */
@Getter
public class TaskContext<I, O> {

    @Setter private I input;
    @Setter private O output;
    @Setter private Map<String, Object> attributes = new ConcurrentHashMap<>();
    private boolean success = true;
    private boolean terminateChain = false;
    @Setter private String currentNodeId;
    private String errorMessage;
    private Throwable exception;
    @Setter private boolean skipCurrentNode = false;
    @Setter private long startTime;
    @Setter private long endTime;

    public void setAttribute(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
            return;
        }
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    public void markFailed(String errorMessage, Throwable exception) {
        this.success = false;
        this.errorMessage = errorMessage;
        this.exception = exception;
    }

    public void markTerminate() {
        this.terminateChain = true;
    }

    public void markSkipCurrentNode() {
        this.skipCurrentNode = true;
    }

    public TaskContext<I, O> copy() {
        TaskContext<I, O> copy = new TaskContext<>();
        copy.input = this.input;
        copy.output = this.output;
        copy.attributes = this.attributes;
        copy.success = this.success;
        copy.terminateChain = this.terminateChain;
        copy.currentNodeId = this.currentNodeId;
        copy.errorMessage = this.errorMessage;
        copy.exception = this.exception;
        copy.skipCurrentNode = this.skipCurrentNode;
        copy.startTime = this.startTime;
        copy.endTime = this.endTime;
        return copy;
    }

    public void mergeError(TaskContext<I, O> other) {
        if (this.success && !other.success) {
            this.success = false;
            this.errorMessage = other.errorMessage;
            this.exception = other.exception;
        }
        if (other.terminateChain) {
            this.terminateChain = true;
        }
    }

    public void mergeData(TaskContext<I, O> other) {
        if (other.output != null) {
            this.output = other.output;
        }
    }
}
