package com.hmdp.shoprating.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.shoprating.domain.model.ShopRatingRefreshMessage;
import com.hmdp.shoprating.domain.model.ShopRatingTriggerSource;
import com.hmdp.shoprating.service.ShopRatingRefreshService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ShopRatingRefreshServiceImpl implements ShopRatingRefreshService {

    private static final String SHOP_RATING_REFRESH_DESTINATION = "shop-rating-refresh-topic:refresh";

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public void triggerFromBlog(Long shopId) {
        ShopRatingRefreshMessage message = new ShopRatingRefreshMessage(
                shopId, ShopRatingTriggerSource.BLOG_PUBLISHED, System.currentTimeMillis());
        rocketMQTemplate.syncSend(SHOP_RATING_REFRESH_DESTINATION,
                org.springframework.messaging.support.MessageBuilder.withPayload(JSONUtil.toJsonStr(message)).build());
        log.info("shop rating refresh message sent from blog, shopId={}", shopId);
    }

    @Override
    public void triggerIfExpired(Long shopId) {
        String key = RedisConstants.SHOP_RATING_REFRESH_TS_KEY + shopId;
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value != null
                && System.currentTimeMillis() - Long.parseLong(value)
                   < TimeUnit.MINUTES.toMillis(RedisConstants.SHOP_RATING_REFRESH_TTL_MINUTES)) {
            return;
        }
        RLock lock = redissonClient.getLock(RedisConstants.SHOP_RATING_REFRESH_LOCK_KEY + shopId);
        if (!lock.tryLock()) {
            return;
        }
        try {
            value = stringRedisTemplate.opsForValue().get(key);
            if (value != null
                    && System.currentTimeMillis() - Long.parseLong(value)
                       < TimeUnit.MINUTES.toMillis(RedisConstants.SHOP_RATING_REFRESH_TTL_MINUTES)) {
                return;
            }
            ShopRatingRefreshMessage message = new ShopRatingRefreshMessage(
                    shopId, ShopRatingTriggerSource.SHOP_DETAIL_EXPIRED, System.currentTimeMillis());
            rocketMQTemplate.syncSend(SHOP_RATING_REFRESH_DESTINATION,
                    org.springframework.messaging.support.MessageBuilder.withPayload(JSONUtil.toJsonStr(message)).build());
            log.info("shop rating refresh message sent from expired check, shopId={}", shopId);
        } finally {
            lock.unlock();
        }
    }
}
