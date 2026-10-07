package com.hmdp.shared.reconciliation.model;

/**
 * 对账业务类型 — 标识触发库存变更的业务场景。
 *
 * <p>不同场景产生的 RESTORE 日志需要区分处理：
 * FAIL 场景需要补偿重试，TIMEOUT 场景标记为正常取消。</p>
 */
public enum ReconcileBusinessType {
    /** 正常成功流程 */
    SUCCESS,
    /** 失败回滚（MQ发送失败、DB库存不足等异常） */
    FAIL,
    /** 超时取消（订单15分钟未支付自动释放库存） */
    TIMEOUT
}
