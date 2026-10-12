package com.hmdp.shared.stateengine.spi;

import com.hmdp.shared.stateengine.model.StateTask;

/**
 * 状态机任务持久化仓库 — SPI 扩展点，定义 StateTask 的存储与查询契约。
 *
 * <p>定位：核心引擎层。StateEngine 在 start() 时调用 save() 持久化新任务，
 * 在节点流转后调用 update() 更新状态，在 resume() 时调用 findByTaskId() 恢复任务。</p>
 *
 * <p>CBB 自带 InMemoryStateTaskRepository 默认实现（ConcurrentHashMap 内存存储），
 * 业务场景可替换为 DB 实现以支持任务状态持久化和跨进程恢复。</p>
 */
public interface StateTaskRepository {

    /**
     * 保存一条全新的 StateTask。
     * <p>StateEngine.start() 在任务首次进入状态机时调用此方法，
     * 将 runtimeStatus=PENDING 的任务写入持久层。</p>
     *
     * @param task 待保存的任务，runtimeStatus 为 PENDING 或 RUNNING
     */
    void save(StateTask task);

    /**
     * 更新已存在的 StateTask。
     * <p>StateEngine 在每次节点执行后（无论成功/失败）调用此方法，
     * 将最新的 currentState、runtimeStatus、retryCount 等字段同步到持久层。</p>
     *
     * @param task 待更新的任务，taskId 作为唯一定位键
     */
    void update(StateTask task);

    /**
     * 根据 taskId 查询 StateTask。
     * <p>StateEngine.resume() 在从 FAILED/MANUAL_REVIEW 状态恢复任务时，
     * 先通过此方法从持久层取出任务快照，再驱动状态机继续执行。</p>
     *
     * @param taskId 任务唯一标识
     * @return 匹配的 StateTask，未找到返回 null
     */
    StateTask findByTaskId(String taskId);
}