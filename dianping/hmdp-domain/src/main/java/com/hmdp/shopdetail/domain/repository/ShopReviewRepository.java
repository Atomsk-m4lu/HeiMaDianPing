package com.hmdp.shopdetail.domain.repository;

import com.hmdp.shopdetail.domain.model.ShopReview;
import com.hmdp.shopdetail.domain.model.ShopReviewSummary;

import java.util.List;

/**
 * 店铺评价仓储接口（Domain 层定义，Infrastructure 层实现）
 * <p>
 * 实现类为 {@link com.hmdp.shopdetail.infrastructure.repository.MybatisShopReviewRepository}。
 * </p>
 */
public interface ShopReviewRepository {

    /** 查询最新评价列表，limit 控制返回条数 */
    List<ShopReview> findByShopId(Long shopId, int limit);

    /** 统计评价汇总（总数、平均分、推荐率、高频标签），内部取最近 100 条计算 */
    ShopReviewSummary summarize(Long shopId);
}
