package com.hmdp.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 秒杀订单MQ消息体 — 在 Redis Lua 脚本扣减成功后，
 * 携带订单核心信息通过 RocketMQ 投递到异步建单流程。
 *
 * <p>消息流向：
 * <ol>
 *   <li>order-create tag → SeckillPlusOrderListener（异步建单）</li>
 *   <li>order-timeout tag → SeckillOrderTimeoutListener（延时14分钟，超时关单）</li>
 * </ol></p>
 */
@Data
public class SeckillVoucherOrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预生成的订单ID（RedisIdWorker：秒级时间戳 + Redis 自增序列） */
    private Long orderId;

    /** 下单用户ID */
    private Long userId;

    /** 秒杀优惠券ID */
    private Long voucherId;

    /** 对账追踪ID，格式 trace-{orderId}，串联整个秒杀对账链路 */
    private String traceId;
}