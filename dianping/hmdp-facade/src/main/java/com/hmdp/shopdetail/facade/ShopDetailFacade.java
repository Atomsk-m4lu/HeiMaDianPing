package com.hmdp.shopdetail.facade;

import com.hmdp.shopdetail.domain.model.GuaranteeClaim;
import com.hmdp.shopdetail.domain.model.ShopDetailAggregate;

/**
 * 店铺详情门面接口
 * <p>
 * 对外暴露商详模块的所有业务能力，Controller、Job、Listener 均通过此接口调用，
 * 不直接依赖 ApplicationService。
 * 这样做的目的不是“多包一层”，而是把外部调用点和内部编排细节解耦：
 * Controller 只需要知道“商详模块提供什么能力”，不需要知道内部是串行聚合还是任务链并行聚合。
 * 实现类：{@link com.hmdp.shopdetail.facade.impl.ShopDetailFacadeImpl}
 * </p>
 */
public interface ShopDetailFacade {

    /** 查询完整店铺详情聚合根 */
    ShopDetailAggregate getShopDetail(Long shopId);

    /** 提交保障申诉，触发补偿流引擎自动流转 */
    GuaranteeClaim submitGuaranteeClaim(GuaranteeClaim claim);

    /** 重试保障申诉，超过最大次数时转人工审核 */
    GuaranteeClaim retryGuaranteeClaim(String claimNo);

    /** 按申诉单号查询申诉详情 */
    GuaranteeClaim getGuaranteeClaim(String claimNo);
}
