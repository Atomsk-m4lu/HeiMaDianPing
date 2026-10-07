package com.hmdp.shopdetail.domain.repository;

import com.hmdp.shopdetail.domain.model.ShopDetailAggregate;
import com.hmdp.shopdetail.domain.model.ShopMarketingOffer;
import com.hmdp.shopdetail.domain.model.ShopVisitBlog;

import java.util.List;

/**
 * 店铺详情仓储接口（Domain 层定义，Infrastructure 层实现）
 * <p>
 * 负责店铺基础信息、附近店铺、优惠券和探店博客的数据存取。
 * 实现类为 {@link com.hmdp.shopdetail.infrastructure.repository.MybatisShopDetailRepository}，
 * 通过适配器复用遗留 Shop/Blog 服务。
 * </p>
 */
public interface ShopDetailRepository {

    /** 查询店铺基础信息，同时附带决策标签和优惠券 */
    ShopDetailAggregate findBaseDetail(Long shopId);

    /** 查询同类型附近店铺，用于构建"周边相关店铺"列表 */
    List<ShopDetailAggregate> findNearbyShops(Long typeId, Double x, Double y, int current);

    /** 查询店铺当前可用的优惠券/套餐列表 */
    List<ShopMarketingOffer> findOffers(Long shopId);

    /** 查询达人探店博客，limit 控制返回条数 */
    List<ShopVisitBlog> findVisitBlogs(Long shopId, int limit);
}
