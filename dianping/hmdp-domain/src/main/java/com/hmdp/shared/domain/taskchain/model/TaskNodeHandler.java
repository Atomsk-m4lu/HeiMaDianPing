package com.hmdp.shared.domain.taskchain.model;

/**
 * 任务链节点处理器接口
 * <p>
 * 所有任务链节点必须实现此接口，并配合 {@link com.hmdp.shared.domain.taskchain.annotation.TaskNode} 注解使用。
 * 节点通过 context 的 setAttribute/getAttribute 共享中间数据，
 * 通过 context.setOutput 写入最终结果。
 * </p>
 *
 * @param <I> 输入类型（通常为 shopId: Long）
 * @param <O> 输出类型（通常为 ShopDetailAggregate）
 */
@FunctionalInterface
public interface TaskNodeHandler<I, O> {

    /**
     * 执行节点逻辑
     *
     * @param context 任务上下文，包含输入、输出和节点间共享的属性
     */
    void execute(TaskContext<I, O> context);
}
