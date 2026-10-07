package com.hmdp.shoprating.service;

import com.hmdp.shared.domain.taskchain.enums.ExecutionMode;
import com.hmdp.shared.domain.taskchain.enums.ProcessPhase;
import com.hmdp.shared.domain.taskchain.model.PhaseConfig;
import com.hmdp.shared.domain.taskchain.service.TaskChainEngine;
import com.hmdp.shoprating.domain.model.ShopRatingRefreshMessage;
import com.hmdp.shoprating.domain.model.ShopRatingRefreshTaskContext;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SHOP_RATING_REFRESH_LOCK_KEY;

@Service
public class ShopRatingRefreshProcessor {

    private final TaskChainEngine taskChainEngine;
    private final RedissonClient redissonClient;

    public ShopRatingRefreshProcessor(TaskChainEngine taskChainEngine,
                                      RedissonClient redissonClient) {
        this.taskChainEngine = taskChainEngine;
        this.redissonClient = redissonClient;
    }

    public void process(ShopRatingRefreshMessage message) {
        RLock lock = redissonClient.getLock(SHOP_RATING_REFRESH_LOCK_KEY + message.getShopId());
        if (!lock.tryLock()) {
            return;
        }
        try {
            ShopRatingRefreshTaskContext context = new ShopRatingRefreshTaskContext();
            context.setInput(message);
            taskChainEngine.executePhaseChain("shop_rating_refresh", context, Arrays.asList(
                    new PhaseConfig(ProcessPhase.PRE_VALIDATION, ExecutionMode.SERIAL),
                    new PhaseConfig(ProcessPhase.DATA_PROCESSING, ExecutionMode.SERIAL),
                    new PhaseConfig(ProcessPhase.DATA_INSERTION, ExecutionMode.PARALLEL)
            ));
        } finally {
            lock.unlock();
        }
    }
}
