package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MQ事件记录实体 — 可靠消息机制的DB兜底。
 *
 * <p>当 RocketMQ 同步发送失败时，消息被持久化到此表，
 * 由 MqEventRetryJob 定时捞取并重试发送，保证消息不丢失。</p>
 *
 * <p>事件状态流转：
 * <pre>{@code
 *   PENDING_RETRY → 重试成功（删除记录）
 *                 → 重试多次失败 → FAILED（人工介入）
 * }</pre></p>
 */
@Data
@Accessors(chain = true)
@TableName("tb_mq_event_record")
public class MqEventRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 事件类型标识（如 SECKILL_ORDER_CREATE、SECKILL_ORDER_TIMEOUT） */
    private String eventType;

    /** RocketMQ 目标 topic:tag */
    private String destination;

    /** 消息体（JSON字符串） */
    private String payload;

    /** 事件状态：PENDING_RETRY=待重试，SENT=已重发成功，FAILED=超过最大重试次数 */
    private String eventStatus;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最近一次失败的错误信息 */
    private String lastError;

    /** 下次重试时间（初始为失败时间+30秒，后续指数退避） */
    private LocalDateTime nextRetryTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
