package com.hmdp.shared.domain.taskchain.model;

import com.hmdp.shared.domain.taskchain.enums.ProcessPhase;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 阶段执行结果
 * <p>
 * 记录某个阶段的执行情况：是否成功、开始/结束时间、错误信息和异常。
 * 通过静态工厂方法 success/fail 创建，构造函数私有。
 * </p>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PhaseExecutionResult {

    private final ProcessPhase phase;
    private final boolean success;
    private final long startTime;
    private final long endTime;
    private final String errorMessage;
    private final Throwable exception;

    public static PhaseExecutionResult success(ProcessPhase phase, long startTime, long endTime) {
        return new PhaseExecutionResult(phase, true, startTime, endTime, null, null);
    }

    public static PhaseExecutionResult fail(ProcessPhase phase, long startTime, long endTime, String errorMessage, Throwable exception) {
        return new PhaseExecutionResult(phase, false, startTime, endTime, errorMessage, exception);
    }
}
