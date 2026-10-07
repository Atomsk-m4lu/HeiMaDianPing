package com.hmdp.shared.reconciliation.model;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 优惠券订单状态枚举 — 订单全生命周期状态。
 *
 * <p>状态流转：
 * <pre>{@code
 *   UNPAID(1) ──→ PAID(2) ──→ VERIFIED(3)
 *            └──→ CANCELLED(4)
 *   PAID(2) ──→ REFUNDING(5) ──→ REFUNDED(6)
 * }</pre></p>
 *
 * <p>秒杀Plus中的关键状态转换：
 * <ul>
 *   <li>异步建单时状态为 UNPAID，并发送延时14分钟的超时消息</li>
 *   <li>超时未支付 → SeckillOrderTimeoutListener 将状态更新为 CANCELLED</li>
 *   <li>超时关单回退Job（SeckillTimeoutCloseFallbackJob）兜底处理未及时关闭的订单</li>
 * </ul></p>
 */
@Getter
public enum VoucherOrderStatus {
    /** 未支付 */
    UNPAID(1),
    /** 已支付 */
    PAID(2),
    /** 已核销 */
    VERIFIED(3),
    /** 已取消 */
    CANCELLED(4),
    /** 退款中 */
    REFUNDING(5),
    /** 已退款 */
    REFUNDED(6);

    /** DB存储的int值 */
    @EnumValue
    private final int code;

    VoucherOrderStatus(int code) {
        this.code = code;
    }
}
