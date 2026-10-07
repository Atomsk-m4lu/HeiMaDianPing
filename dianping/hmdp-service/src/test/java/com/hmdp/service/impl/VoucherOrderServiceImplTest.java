package com.hmdp.service.impl;

import com.hmdp.dto.UserDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillVoucherOrderMessage;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VoucherOrderServiceImplTest {

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void seckillVoucherPlus_shouldRollbackRedisStateWhenMessageSendFails() {
        VoucherOrderServiceImpl service = new VoucherOrderServiceImpl();
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        RedisIdWorker redisIdWorker = mock(RedisIdWorker.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        RocketMqReliablePublisher reliablePublisher = mock(RocketMqReliablePublisher.class);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "redisIdWorker", redisIdWorker);
        ReflectionTestUtils.setField(service, "rocketMQTemplate", rocketMQTemplate);
        ReflectionTestUtils.setField(service, "rocketMqReliablePublisher", reliablePublisher);

        UserDTO user = new UserDTO();
        user.setId(9L);
        UserHolder.saveUser(user);
        when(redisIdWorker.nextId("order")).thenReturn(101L);
        when(stringRedisTemplate.execute(any(), eq(Collections.emptyList()), eq("7"), eq("9"), eq("101"),
                eq("trace-101"), any(), any()))
                .thenReturn(0L);
        when(reliablePublisher.publish(eq("SECKILL_ORDER_CREATE"), eq("seckill-plus-order-topic:order-create"), any()))
                .thenReturn(false);

        Result result = service.seckillVoucherPlus(7L);

        assertTrue(!result.getSuccess());
        assertEquals("下单人数过多，请重试", result.getErrorMsg());
        verify(stringRedisTemplate, times(2)).execute(any(), eq(Collections.emptyList()), eq("7"), eq("9"),
                eq("101"), eq("trace-101"), any(), any());
    }

    @Test
    void seckillVoucherPlus_shouldRejectFrequentRequests() {
        VoucherOrderServiceImpl service = new VoucherOrderServiceImpl();
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        RedisIdWorker redisIdWorker = mock(RedisIdWorker.class);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "redisIdWorker", redisIdWorker);

        UserDTO user = new UserDTO();
        user.setId(9L);
        UserHolder.saveUser(user);
        when(redisIdWorker.nextId("order")).thenReturn(101L);
        when(stringRedisTemplate.execute(any(), eq(Collections.emptyList()), eq("7"), eq("9"), eq("101"),
                eq("trace-101"), any(), any()))
                .thenReturn(3L);

        Result result = service.seckillVoucherPlus(7L);

        assertTrue(!result.getSuccess());
        assertEquals("请求过于频繁，请稍后重试", result.getErrorMsg());
    }

    @Test
    void rollbackSeckillVoucherPlus_shouldBeIdempotent() {
        VoucherOrderServiceImpl service = new VoucherOrderServiceImpl();
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);

        com.hmdp.entity.VoucherOrder order = new com.hmdp.entity.VoucherOrder();
        order.setId(101L);
        order.setVoucherId(7L);
        order.setUserId(9L);
        order.setTraceId("trace-101");

        service.rollbackSeckillVoucherPlus(order);

        verify(stringRedisTemplate).execute(any(), eq(Collections.emptyList()), eq("7"), eq("9"),
                eq("101"), eq("trace-101"), any(), any());
    }

    @Test
    void seckillVoucherPlus_shouldAcceptOrderWhenMessageSendSucceedsBeforeConsumerPersists() {
        VoucherOrderServiceImpl service = new VoucherOrderServiceImpl();
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        RedisIdWorker redisIdWorker = mock(RedisIdWorker.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        RocketMqReliablePublisher reliablePublisher = mock(RocketMqReliablePublisher.class);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "redisIdWorker", redisIdWorker);
        ReflectionTestUtils.setField(service, "rocketMQTemplate", rocketMQTemplate);
        ReflectionTestUtils.setField(service, "rocketMqReliablePublisher", reliablePublisher);

        UserDTO user = new UserDTO();
        user.setId(9L);
        UserHolder.saveUser(user);
        when(redisIdWorker.nextId("order")).thenReturn(101L);
        when(stringRedisTemplate.execute(any(), eq(Collections.emptyList()), eq("7"), eq("9"), eq("101"),
                eq("trace-101"), any(), any()))
                .thenReturn(0L);
        when(reliablePublisher.publish(eq("SECKILL_ORDER_CREATE"), eq("seckill-plus-order-topic:order-create"), any()))
                .thenReturn(true);
        when(reliablePublisher.publishDelay(eq("SECKILL_ORDER_TIMEOUT"), eq("seckill-plus-order-topic:order-timeout"), any(), eq(14)))
                .thenReturn(true);

        Result result = service.seckillVoucherPlus(7L);

        assertTrue(result.getSuccess());
        assertEquals(101L, result.getData());
        verify(reliablePublisher).publish(eq("SECKILL_ORDER_CREATE"), eq("seckill-plus-order-topic:order-create"),
                argThat(payload -> {
                    SeckillVoucherOrderMessage message = cn.hutool.json.JSONUtil.toBean((String) payload, SeckillVoucherOrderMessage.class);
                    return "trace-101".equals(message.getTraceId())
                            && Long.valueOf(101L).equals(message.getOrderId())
                            && Long.valueOf(7L).equals(message.getVoucherId())
                            && Long.valueOf(9L).equals(message.getUserId());
                }));
        verify(reliablePublisher).publishDelay(eq("SECKILL_ORDER_TIMEOUT"), eq("seckill-plus-order-topic:order-timeout"), any(), eq(14));
        verify(stringRedisTemplate).execute(any(), eq(Collections.emptyList()), eq("7"), eq("9"),
                eq("101"), eq("trace-101"), any(), any());
    }

    @Test
    void seckillVoucherPlus_shouldAcceptOrderWhenMessageSendSucceeds() {
        VoucherOrderServiceImpl service = new VoucherOrderServiceImpl();
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        RedisIdWorker redisIdWorker = mock(RedisIdWorker.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        RocketMqReliablePublisher reliablePublisher = mock(RocketMqReliablePublisher.class);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "redisIdWorker", redisIdWorker);
        ReflectionTestUtils.setField(service, "rocketMQTemplate", rocketMQTemplate);
        ReflectionTestUtils.setField(service, "rocketMqReliablePublisher", reliablePublisher);

        UserDTO user = new UserDTO();
        user.setId(9L);
        UserHolder.saveUser(user);
        when(redisIdWorker.nextId("order")).thenReturn(101L);
        when(stringRedisTemplate.execute(any(), eq(Collections.emptyList()), eq("7"), eq("9"), eq("101"),
                eq("trace-101"), any(), any()))
                .thenReturn(0L);
        when(reliablePublisher.publish(eq("SECKILL_ORDER_CREATE"), eq("seckill-plus-order-topic:order-create"), any()))
                .thenReturn(true);
        when(reliablePublisher.publishDelay(eq("SECKILL_ORDER_TIMEOUT"), eq("seckill-plus-order-topic:order-timeout"), any(), eq(14)))
                .thenReturn(true);

        Result result = service.seckillVoucherPlus(7L);

        assertTrue(result.getSuccess());
        assertEquals(101L, result.getData());
    }

    @Test
    void loadDbStock_shouldUseDirectLookupCompatibleWithJdk21() {
        VoucherOrderServiceImpl service = new VoucherOrderServiceImpl();
        ISeckillVoucherService seckillVoucherService = mock(ISeckillVoucherService.class);
        ReflectionTestUtils.setField(service, "seckillVoucherService", seckillVoucherService);

        SeckillVoucher voucher = new SeckillVoucher().setVoucherId(13L).setStock(100);
        when(seckillVoucherService.getById(13L)).thenReturn(voucher);
        Integer stock = (Integer) ReflectionTestUtils.invokeMethod(service, "loadDbStock", 13L);

        assertEquals(100, stock);
        verify(seckillVoucherService).getById(13L);
    }
}
