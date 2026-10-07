package com.hmdp.service.impl;

import com.hmdp.entity.MqEventRecord;
import com.hmdp.mapper.MqEventRecordMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RocketMqReliablePublisherTest {

    @Test
    void publish_shouldPersistFailureRecordWhenSendThrows() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        MqEventRecordMapper mapper = mock(MqEventRecordMapper.class);
        RocketMqReliablePublisher publisher = new RocketMqReliablePublisher(rocketMQTemplate, mapper);
        doThrow(new RuntimeException("mq down")).when(rocketMQTemplate)
                .syncSend(eq("topic:tag"), any(Message.class));

        boolean success = publisher.publish("SECKILL_ORDER_CREATE", "topic:tag", "{\"id\":1}");

        assertFalse(success);
        verify(mapper).insert(any(MqEventRecord.class));
    }

    @Test
    void publish_shouldReturnTrueWhenSendSucceeds() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        MqEventRecordMapper mapper = mock(MqEventRecordMapper.class);
        RocketMqReliablePublisher publisher = new RocketMqReliablePublisher(rocketMQTemplate, mapper);

        boolean success = publisher.publish("SECKILL_ORDER_CREATE", "topic:tag", "{\"id\":1}");

        assertTrue(success);
    }
}
