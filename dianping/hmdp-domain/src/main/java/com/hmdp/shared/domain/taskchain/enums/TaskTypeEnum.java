package com.hmdp.shared.domain.taskchain.enums;

/**
 * 任务类型枚举
 * <p>
 * 用于 {@link com.hmdp.shared.domain.taskchain.annotation.TaskNode#taskType()} 声明节点的资源特征，
 * {@link com.hmdp.shared.domain.taskchain.service.TaskDispatcher} 据此选择对应的线程池：
 * <ul>
 *   <li>IO_BOUND → ioExecutor（线程数较多，适合等待 IO）</li>
 *   <li>CPU_BOUND → cpuExecutor（线程数 = CPU 核数，避免上下文切换）</li>
 * </ul>
 * </p>
 */
public enum TaskTypeEnum {
    /** CPU 密集型：纯内存计算，无 IO 等待 */
    CPU_BOUND,
    /** IO 密集型：涉及数据库、缓存、RPC 等 IO 操作 */
    IO_BOUND
}
