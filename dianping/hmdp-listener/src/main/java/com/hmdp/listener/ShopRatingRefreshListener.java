package com.hmdp.listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.shoprating.domain.model.ShopRatingRefreshMessage;
import com.hmdp.shoprating.service.ShopRatingRefreshProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "shop-rating-refresh-topic",
        consumerGroup = "shop-rating-refresh-consumer-group",
        selectorExpression = "refresh"
)
public class ShopRatingRefreshListener implements RocketMQListener<String> {

    private final ShopRatingRefreshProcessor processor;

    @Override
    public void onMessage(String message) {
        ShopRatingRefreshMessage refreshMessage = JSONUtil.toBean(message, ShopRatingRefreshMessage.class);
        log.info("shop rating refresh message received, shopId={}, source={}",
                refreshMessage.getShopId(), refreshMessage.getTriggerSource());
        processor.process(refreshMessage);
    }
}
