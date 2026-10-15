package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hmdp.shared.reconciliation.model.VoucherOrderStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券订单实体 — 秒杀/普通购买的核心订单模型。
 *
 * <p>秒杀Plus流程中，订单ID由 RedisIdWorker（秒级时间戳 + Redis 自增序列）在 Redis Lua 执行前预生成，
 * 保证即使在分布式环境下也能唯一标识一笔订单。</p>
 *
 * <p>订单状态流转：
 * <pre>{@code
 *   UNPAID → PAID → VERIFIED（已核销）
 *         → CANCELLED（超时/手动取消）
 *         → REFUNDING → REFUNDED（退款流程）
 * }</pre></p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_voucher_order")
public class VoucherOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 — 由 RedisIdWorker 预生成（IdType.INPUT），非 DB 自增 */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /** 下单的用户ID */
    private Long userId;

    /** 购买的代金券ID */
    private Long voucherId;

    /** 支付方式：1=余额支付，2=支付宝，3=微信 */
    private Integer payType;

    /** 订单状态，详见 VoucherOrderStatus 枚举 */
    private VoucherOrderStatus status;

    /** 下单时间 */
    private LocalDateTime createTime;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 核销时间 */
    private LocalDateTime useTime;

    /** 退款时间 */
    private LocalDateTime refundTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 对账追踪ID — 不持久化到DB，仅用于内存传递。
     * 格式 trace-{orderId}，串联 Lua脚本 → MQ消息 → 对账引擎。
     */
    @TableField(exist = false)
    private String traceId;
}