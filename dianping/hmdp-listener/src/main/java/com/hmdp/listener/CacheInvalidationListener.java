package com.hmdp.listener;

import com.hmdp.utils.MultiLevelCacheService;
import com.hmdp.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.stereotype.Component;

/**
 * 缓存失效广播监听器（批次3 ②）—— <b>BROADCASTING 模式</b>：同一消费者组内每个实例都会收到同一条消息。
 *
 * <p>与默认 CLUSTERING 模式的区别是本设计的关键：集群模式下消息只在组内投递给其中一个实例，
 * 其他实例的本地缓存永远不会被清理（参考实现在这里踩过坑）；广播模式保证每实例必达。
 * 广播本身是尽力而为，真正的兜底是 L1 条目自身的逻辑过期时间。</p>
 *
 * <p>收到消息的动作是"双层驱逐"（L1 本地 + Redis）：Redis 的重复删除是幂等的，
 * 顺手把可能被并发读请求重新装入的脏值也清掉。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MultiLevelCacheService.CACHE_INVALIDATION_TOPIC,
        consumerGroup = "hmdp-cache-invalidation-broadcast-group",
        selectorExpression = MultiLevelCacheService.CACHE_INVALIDATION_TAG,
        messageModel = MessageModel.BROADCASTING
)
public class CacheInvalidationListener implements RocketMQListener<String>, RocketMQPushConsumerLifecycleListener {

    private final MultiLevelCacheService multiLevelCacheService;

    /**
     * 冷启动语义修正：默认 CONSUME_FROM_LAST_OFFSET 会跳过"消费者首次拉取之前"投递的消息
     * （新 topic 场景下首条失效消息会被直接吞掉——实测验证时抓到的坑）。
     * 失效消息是幂等的驱逐指令，宁多勿漏，因此改为从头消费；重复驱逐完全无害。
     */
    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
    }

    @Override
    public void onMessage(String message) {
        multiLevelCacheService.evict(RedisConstants.CACHE_SHOP_KEY, message);
        log.info("[多级缓存] 收到广播失效，已清理本地+Redis 缓存, shopId={}", message);
    }
}
