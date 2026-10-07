package com.hmdp.shoprating.task;

import com.hmdp.shared.domain.taskchain.annotation.TaskNode;
import com.hmdp.shared.domain.taskchain.enums.ProcessPhase;
import com.hmdp.shared.domain.taskchain.enums.TaskTypeEnum;
import com.hmdp.shared.domain.taskchain.model.TaskContext;
import com.hmdp.shared.domain.taskchain.model.TaskNodeHandler;
import com.hmdp.shoprating.domain.model.ShopRatingRefreshMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Component
@TaskNode(
        nodeId = "delete_shop_cache",
        group = "shop_rating_refresh",
        phase = ProcessPhase.DATA_INSERTION,
        order = 20,
        taskType = TaskTypeEnum.IO_BOUND,
        skippable = true
)
public class DeleteShopCacheNode implements TaskNodeHandler<ShopRatingRefreshMessage, Void> {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void execute(TaskContext<ShopRatingRefreshMessage, Void> context) {
        stringRedisTemplate.delete(CACHE_SHOP_KEY + context.getInput().getShopId());
    }
}
