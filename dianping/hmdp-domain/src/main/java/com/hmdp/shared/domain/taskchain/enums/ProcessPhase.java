package com.hmdp.shared.domain.taskchain.enums;

/**
 * 任务链执行阶段枚举
 * <p>
 * 定义了任务链的五个标准阶段，按 order 从小到大依次执行。
 * 商详链路目前只使用 DATA_PROCESSING（并行查询）和 POST_PROCESSING（组装聚合根）。
 * </p>
 */
public enum ProcessPhase {
    /** 前置校验：参数合法性检查 */
    PRE_VALIDATION(10, "pre_validation"),
    /** 数据判断：决定后续走哪条分支 */
    PRE_PROCESSING(20, "pre_processing"),
    /** 数据处理：核心查询/计算阶段，支持并行 */
    DATA_PROCESSING(30, "data_processing"),
    /** 数据写入：持久化或缓存写入 */
    DATA_INSERTION(40, "data_insertion"),
    /** 后置处理：结果组装、数据丰富 */
    POST_PROCESSING(50, "post_processing");

    private final int order;
    private final String desc;

    ProcessPhase(int order, String desc) {
        this.order = order;
        this.desc = desc;
    }

    public int getOrder() {
        return order;
    }

    public String getDesc() {
        return desc;
    }
}
