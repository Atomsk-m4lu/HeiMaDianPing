package com.hmdp.shared.reconciliation.model;

/**
 * 对账日志状态枚举。
 *
 * <p>状态流转图：
 * <pre>{@code
 *   NEW ──→ CHECKING ──┬──→ COMPLETED（对账通过）
 *                      └──→ MISMATCH_FOUND（发现差异）
 *                              └──→ COMPENSATING
 *                                      ├──→ VERIFIED_FIXED → COMPLETED（补偿成功）
 *                                      ├──→ RETRYING（补偿失败，等待重试，retryCount < max）
 *                                      └──→ MANUAL_REVIEW（超过最大重试次数，人工介入）
 *                                              └──→ CLOSED（人工确认关闭）
 * }</pre></p>
 */
public enum ReconciliationStatus {
    /** 待对账 — 对账日志已写入，等待引擎处理 */
    PENDING,
    /** 新建任务 — 对账引擎刚创建该任务 */
    NEW,
    /** 正在校验 — 引擎正在执行 rule.matches() */
    CHECKING,
    /** 发现差异 — rule.matches() 返回 false，Redis 与 DB 库存不一致 */
    MISMATCH_FOUND,
    /** 正在补偿 */
    COMPENSATING,
    /** 已验证修复 — 补偿成功，旁路校验通过 */
    VERIFIED_FIXED,
    /** 对账完成 — 数据一致，任务正常终态 */
    COMPLETED,
    /** 等待重试 — 补偿失败，在 retryCount 范围内等待下次调度 */
    RETRYING,
    /** 人工审核 — retryCount 超过 maxRetryCount，升级为人工处理 */
    MANUAL_REVIEW,
    /** 已关闭 — 人工审核后的终态 */
    CLOSED
}
