package com.hmdp.service.impl;

import com.hmdp.entity.MqEventRecord;
import com.hmdp.mapper.MqEventRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * RocketMQ 可靠消息发布器 — 同步发送 + 失败DB兜底。
 *
 * <p>可靠性保证：
 * <ul>
 *   <li>正常路径：syncSend 同步发送到 RocketMQ Broker</li>
 *   <li>失败路径：消息持久化到 tb_mq_event_record 表，由 MqEventRetryJob 定时重试</li>
 * </ul></p>
 *
 * <p>使用场景（秒杀Plus中）：
 * <ol>
 *   <li>发送 order-create 消息 → SeckillPlusOrderListener 异步建单</li>
 *   <li>发送 order-timeout 延时消息 → SeckillOrderTimeoutListener 超时关单</li>
 * </ol></p>
 *
 * <p>注意：publish 返回 false 仅表示MQ发送失败且已DB兜底，调用方应自行处理业务回滚逻辑。</p>
 */
@Component
@Slf4j
public class RocketMqReliablePublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final MqEventRecordMapper mqEventRecordMapper;

    public RocketMqReliablePublisher(RocketMQTemplate rocketMQTemplate,
                                     MqEventRecordMapper mqEventRecordMapper) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.mqEventRecordMapper = mqEventRecordMapper;
    }

    /**
     * 同步发送可靠消息 — 失败时自动持久化到DB等待重试。
     *
     * @param eventType   事件类型标识（如 SECKILL_ORDER_CREATE）
     * @param destination RocketMQ topic:tag
     * @param payload     消息体JSON
     * @return true=发送成功，false=发送失败已DB兜底
     */
    public boolean publish(String eventType, String destination, String payload) {
        try {
            rocketMQTemplate.syncSend(destination, MessageBuilder.withPayload(payload).build());
            return true;
        } catch (Exception e) {
            log.error("rocketmq publish failed, destination={}", destination, e);
            persistFailure(eventType, destination, payload, e);
            return false;
        }
    }

    /**
     * 发送延时消息 — 失败时同样DB兜底。
     *
     * <p>延时级别（RocketMQ默认）：
     * 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     * 对应levels：1=1s, 2=5s, ..., 14=10m, 15=20m（实际14≈10分钟常见配置）</p>
     *
     * @param eventType   事件类型
     * @param destination RocketMQ topic:tag
     * @param payload     消息体JSON
     * @param delayLevel  延时级别（1-18）
     * @return true=发送成功，false=发送失败已DB兜底
     */
    public boolean publishDelay(String eventType, String destination, String payload, int delayLevel) {
        try {
            rocketMQTemplate.syncSend(destination,
                    MessageBuilder.withPayload(payload)
                            .setHeader("DELAY", delayLevel)
                            .build());
            return true;
        } catch (Exception e) {
            log.error("rocketmq delayed publish failed, destination={}, delayLevel={}", destination, delayLevel, e);
            persistFailure(eventType, destination, payload, e);
            return false;
        }
    }

    /**
     * 持久化发送失败的消息到DB — MqEventRetryJob 定时捞取重试。
     *
     * <p>初始重试时间设为30秒后，retryCount=0，
     * 由 MqEventRetryJob 按指数退避策略逐步增加重试间隔。</p>
     */
    private void persistFailure(String eventType, String destination, String payload, Exception e) {
        MqEventRecord record = new MqEventRecord()
                .setEventType(eventType)
                .setDestination(destination)
                .setPayload(payload)
                .setEventStatus("PENDING_RETRY")
                .setRetryCount(0)
                .setLastError(e.getMessage())
                .setNextRetryTime(LocalDateTime.now().plusSeconds(30))
                .setCreateTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now());
        mqEventRecordMapper.insert(record);
    }
}
