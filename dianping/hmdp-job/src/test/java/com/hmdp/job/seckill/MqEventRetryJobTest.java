package com.hmdp.job.seckill;

import com.hmdp.entity.MqEventRecord;
import com.hmdp.mapper.MqEventRecordMapper;
import com.hmdp.service.impl.RocketMqReliablePublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    // ===== 批次1 ⑤：重试终态判定规则 =====

    @Test
    void belowMaxKeepsPendingRetry() {
        assertEquals("PENDING_RETRY", jobWithMax(5).resolveStatusAfterFailure(4));
    }

    @Test
    void reachMaxMarksFailed() {
        assertEquals("FAILED", jobWithMax(5).resolveStatusAfterFailure(5));
    }

    @Test
    void beyondMaxStaysFailed() {
        assertEquals("FAILED", jobWithMax(5).resolveStatusAfterFailure(6));
    }

    private MqEventRetryJob jobWithMax(int maxRetryCount) {
        MqEventRetryJob job = new MqEventRetryJob();
        ReflectionTestUtils.setField(job, "maxRetryCount", maxRetryCount);
        return job;
    }

    @Test
    void run_shouldMarkFailedWhenRetryExhausted() {
        MqEventRetryJob job = new MqEventRetryJob();
        ReflectionTestUtils.setField(job, "maxRetryCount", 5);
        MqEventRecordMapper mapper = mock(MqEventRecordMapper.class);
        RocketMqReliablePublisher publisher = mock(RocketMqReliablePublisher.class);
        ReflectionTestUtils.setField(job, "mqEventRecordMapper", mapper);
        ReflectionTestUtils.setField(job, "rocketMqReliablePublisher", publisher);

        MqEventRecord row = new MqEventRecord()
                .setId(1L)
                .setEventType("SECKILL_ORDER_CREATE")
                .setDestination("no-such-topic:tag")
                .setPayload("{}")
                .setEventStatus("PENDING_RETRY")
                .setRetryCount(4)
                .setNextRetryTime(LocalDateTime.now());
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(row));
        when(publisher.resend(anyString(), anyString(), anyString())).thenReturn(false);

        job.run();

        ArgumentCaptor<MqEventRecord> captor = ArgumentCaptor.forClass(MqEventRecord.class);
        verify(mapper).updateById(captor.capture());
        assertEquals("FAILED", captor.getValue().getEventStatus());
        assertEquals(5, captor.getValue().getRetryCount());
    }
}
