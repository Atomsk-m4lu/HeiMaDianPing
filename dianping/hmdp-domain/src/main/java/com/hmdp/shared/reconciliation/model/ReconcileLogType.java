package com.hmdp.shared.reconciliation.model;

/**
 * 对账日志类型 — 标识本次库存变更的方向。
 *
 * <p>用于 VoucherReconcileLog.logType 和 Lua 脚本中的 logType 字段，
 * 对账引擎通过比对同一 traceId 下的 DEDUCT/RESTORE 日志来判断库存是否一致。</p>
 */
public enum ReconcileLogType {
    /** 库存扣减 — 用户抢到秒杀券，库存-1 */
    DEDUCT,
    /** 库存回补 — 订单创建失败/超时取消/回滚，库存+1 */
    RESTORE
}
