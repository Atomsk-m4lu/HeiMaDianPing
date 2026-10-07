package com.hmdp.job.seckill;

import com.hmdp.mapper.MqEventRecordMapper;
import com.hmdp.service.impl.RocketMqReliablePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MqEventRetryJobTest {

    @Test
    void run_shouldReturnWhenDependenciesMissingOrNoPendingEvents() {
        MqEventRetryJob job = new MqEventRetryJob();
        MqEventRecordMapper mapper = mock(MqEventRecordMapper.class);
        RocketMqReliablePublisher publisher = mock(RocketMqReliablePublisher.class);
        ReflectionTestUtils.setField(job, "mqEventRecordMapper", mapper);
        ReflectionTestUtils.setField(job, "rocketMqReliablePublisher", publisher);

        job.run();

        verifyNoInteractions(publisher);
    }
}
