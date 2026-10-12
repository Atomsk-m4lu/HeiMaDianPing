package com.hmdp.job.seckill;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.MqEventRecord;
import com.hmdp.mapper.MqEventRecordMapper;
import com.hmdp.service.impl.RocketMqReliablePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MQ事件重试Job — 每30秒捞取 PENDING_RETRY 状态的消息，重新尝试发送。
 *
 * <p>工作流程：
 * <pre>{@code
 *   每30秒 → 查询 PENDING_RETRY 且 next_retry_time <= now 的记录（最多20条）
 *     ├─ 重新发送成功 → event_status = SENT
 *     └─ 再次失败 → retryCount + 1, next_retry_time = now + 30秒
 * }</pre></p>
 *
 * <p>重试策略：固定30秒间隔，不做指数退避（秒杀场景对延迟敏感）。
 * 如果长期失败，人工查看 MqEventRecord 表处理。</p>
 */
@Component
@Slf4j
public class MqEventRetryJob {

    /** 查询 / 更新 MQ 事件记录表 —— 扫描待重试消息、回写发送结果 */
    @Resource
    private MqEventRecordMapper mqEventRecordMapper;

    /** 可靠消息发布器 —— 将待重试记录重新发送到 RocketMQ */
    @Resource
    private RocketMqReliablePublisher rocketMqReliablePublisher;

    /**
     * 扫描并重试 MQ 发送失败的事件
     * <p>
     * cron = "0/30 * * * * ?" 含义：从第 0 秒开始，每 30 秒触发一次（每分钟的 :00 和 :30）。
     * 单轮最多处理 20 条，避免某轮积压太多消息导致 MQ 压力骤增。
     * </p>
     * <p>
     * 处理逻辑（每条记录独立 try-catch，互不影响）：
     * <pre>{@code
     * 发送成功 → event_status=SENT，不再重试
     * 发送失败 → retryCount+1，next_retry_time 推迟 30 秒，等待下轮
     * 抛异常   → 打 WARN 日志后跳过，防止一条坏消息阻断整个 Job
     * }</pre></p>
     */
    @Scheduled(cron = "0/30 * * * * ?")
    public void run() {
        // 防御性空值检查 —— 某些启动阶段 Bean 可能尚未注入完成
        if (mqEventRecordMapper == null || rocketMqReliablePublisher == null) {
            return;
        }
        QueryWrapper<MqEventRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("event_status", "PENDING_RETRY")
                .le("next_retry_time", LocalDateTime.now())
                .last("limit 20");
        List<MqEventRecord> records = mqEventRecordMapper.selectList(wrapper);
        for (MqEventRecord record : records) {
            try {
                boolean success = rocketMqReliablePublisher.publish(
                        record.getEventType(),
                        record.getDestination(),
                        record.getPayload()
                );
                if (success) {
                    // 重试发送成功 → 标记为已发送，不再被后续 Job 扫描到
                    record.setEventStatus("SENT");
                    record.setUpdateTime(LocalDateTime.now());
                    mqEventRecordMapper.updateById(record);
                    continue;
                }
                // 再次失败 → 增加重试计数，推迟到 30 秒后再试
                record.setRetryCount(record.getRetryCount() == null ? 1 : record.getRetryCount() + 1);
                record.setNextRetryTime(LocalDateTime.now().plusSeconds(30));
                record.setUpdateTime(LocalDateTime.now());
                mqEventRecordMapper.updateById(record);
            } catch (Exception e) {
                // 单条异常不影响其他记录，只打 WARN 等待下轮重试
                log.warn("mq event retry failed, id={}", record.getId(), e);
            }
        }
    }
}