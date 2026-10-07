package com.hmdp.shared.domain.taskchain.enums;

/**
 * 阶段执行模式
 * <p>
 * 在 {@link com.hmdp.shared.domain.taskchain.model.PhaseConfig} 中指定，
 * 决定该阶段内的节点是串行还是并行执行。
 * </p>
 */
public enum ExecutionMode {
    /** 串行执行，节点按 order 顺序依次运行 */
    SERIAL("serial"),
    /** 并行执行，节点通过线程池并发运行，全部完成后进入下一阶段 */
    PARALLEL("parallel");

    private final String desc;

    ExecutionMode(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
