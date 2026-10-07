package com.hmdp.shared.domain.compensation.model;

/**
 * 无忧闪赔状态枚举 — 定义闪赔状态机的所有业务状态码。
 *
 * <p>定位：无忧闪赔状态机层。</p>
 *
 * <p>状态流转图：
 * <pre>{@code
 * SUBMITTED ──(申诉原因有效)──→ APPROVED ──→ COMPLETED (终态)
 *      │
 *      └──(申诉原因为空)──→ MANUAL_REVIEW (终态，转人工)
 * }</pre></p>
 *
 * <p>与秒杀对账状态机不同，无忧闪赔没有重试路径：
 * 如果业务校验失败（申诉原因为空），直接转人工审核；
 * 如果校验通过（原因有效），直接审批通过并完成。</p>
 */
public enum FlashPayStateCode {
    /** 已提交 — 用户提交闪赔申请后的初始状态 */
    SUBMITTED,
    /** 已审批 — 系统自动审批通过，准备执行赔付 */
    APPROVED,
    /** 已完成 — 终态，赔付处理完毕 */
    COMPLETED,
    /** 人工审核 — 业务校验不通过（如申诉原因为空），转人工处理 */
    MANUAL_REVIEW;

    /** 状态机唯一标识，用于注册到 StateMachineRegistry */
    public static final String MACHINE_CODE = "FLASH_PAY";
}
