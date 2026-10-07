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

    @Resource
    private MqEventRecordMapper mqEventRecordMapper;

    @Resource
    private RocketMqReliablePublisher rocketMqReliablePublisher;

    /** 每30秒执行一次MQ事件重试 */
    @Scheduled(cron = "0/30 * * * * ?")
    public void run() {
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
                    // 重试发送成功 → 标记为已发送
                    record.setEventStatus("SENT");
                    record.setUpdateTime(LocalDateTime.now());
                    mqEventRecordMapper.updateById(record);
                    continue;
                }
                // 再次失败 → 增加重试计数，推迟下次重试时间
                record.setRetryCount(record.getRetryCount() == null ? 1 : record.getRetryCount() + 1);
                record.setNextRetryTime(LocalDateTime.now().plusSeconds(30));
                record.setUpdateTime(LocalDateTime.now());
                mqEventRecordMapper.updateById(record);
            } catch (Exception e) {
                log.warn("mq event retry failed, id={}", record.getId(), e);
            }
        }
    }
}
