package com.hmdp.shared.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 任务链线程池配置
 * <p>
 * 为任务链引擎提供两个独立的线程池，避免 CPU 密集型和 IO 密集型任务互相抢占资源：
 * <ul>
 *   <li>taskChainCpuExecutor：固定 4 线程，用于纯内存计算节点（CPU_BOUND）</li>
 *   <li>taskChainIoExecutor：固定 8 线程，用于数据库/缓存/RPC 查询节点（IO_BOUND）</li>
 * </ul>
 * 线程数可根据实际机器配置和压测结果调整。
 * </p>
 */
@Configuration
public class TaskChainExecutorConfig {

    @Bean("taskChainCpuExecutor")
    public Executor taskChainCpuExecutor() {
        return Executors.newFixedThreadPool(4);
    }

    @Bean("taskChainIoExecutor")
    public Executor taskChainIoExecutor() {
        return Executors.newFixedThreadPool(8);
    }
}
