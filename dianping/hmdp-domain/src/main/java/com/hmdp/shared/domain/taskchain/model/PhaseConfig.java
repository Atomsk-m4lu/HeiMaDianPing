package com.hmdp.shared.domain.taskchain.model;

import com.hmdp.shared.domain.taskchain.enums.ExecutionMode;
import com.hmdp.shared.domain.taskchain.enums.ProcessPhase;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 阶段配置
 * <p>
 * 描述任务链某个阶段的执行策略：执行模式（串行/并行）、失败是否终止链路、超时时间。
 * 由调用方在执行时传入，不同业务场景可以为同一阶段配置不同策略。
 * </p>
 */
@Getter
@AllArgsConstructor
public class PhaseConfig {

    private final ProcessPhase phase;
    private final ExecutionMode executionMode;
    private final boolean failOnError;
    private final long timeout;

    public PhaseConfig(ProcessPhase phase, ExecutionMode executionMode) {
        this(phase, executionMode, true, 0L);
    }
}
