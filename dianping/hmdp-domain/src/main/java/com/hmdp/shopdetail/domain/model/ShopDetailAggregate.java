package com.hmdp.shopdetail.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 店铺详情聚合根
 * <p>
 * DDD 聚合根，代表一个完整的店铺详情页所需的所有数据。
 * 包含店铺基础信息、评价、优惠券、探店博客、保障计划等子对象。
 * 由 {@link com.hmdp.shopdetail.domain.service.ShopDetailDomainService} 负责组装和丰富。
 * </p>
 */
@Data
public class ShopDetailAggregate {

    /** 店铺主键，整个商详聚合的业务主标识。 */
    private Long shopId;
    /** 店铺名称，前端首屏标题直接展示。 */
    private String shopName;
    /** 店铺分类 ID，用于同类推荐、周边同品类店铺查询。 */
    private Long typeId;
    /** 店铺图片 CSV，后续会在 extraInfo 中拆成相册列表。 */
    private String images;
    /** 所属商圈/区域名称，用于商圈标签和周边推荐文案。 */
    private String area;
    /** 详细地址，前端展示和交通提示都依赖它。 */
    private String address;
    /** 经度，用于距离计算和同类附近店铺检索。 */
    private Double x;
    /** 纬度，用于距离计算和同类附近店铺检索。 */
    private Double y;
    /** 与当前用户或查询点的距离，单位通常是米。 */
    private Double distance;
    /** 人均消费，前端价格带展示和“适合聚餐/轻量到店”判断都依赖它。 */
    private Long avgPrice;
    /** 已售数量，体现店铺热度。 */
    private Integer sold;
    /** 评论总数，通常来自店铺基础表里的历史统计。 */
    private Integer comments;
    /** 店铺综合分，兼容原始店铺表中的评分字段。 */
    private Integer score;
    /** 营业时间文案，例如“10:00-22:00”。 */
    private String openHours;
    /** 决策标签，属于领域服务基于评价/内容/基础信息推导出的展示信号。 */
    private List<ShopDecisionTag> tags = new ArrayList<>();
    /** 可购买的营销优惠，如代金券、套餐券。 */
    private List<ShopMarketingOffer> vouchers = new ArrayList<>();
    /** 达人探店或用户到店内容，用来增强“真实体验感”。 */
    private List<ShopVisitBlog> blogs = new ArrayList<>();
    /** 正式店铺评价列表，和 blogs 不同，它是结构化履约评价。 */
    private List<ShopReview> reviews = new ArrayList<>();
    /** 评价汇总视图，包含推荐率、标签、总评价数等。 */
    private ShopReviewSummary reviewSummary;
    /** 到店须知类扩展信息，如交通建议、设施、营业提示。 */
    private ShopExtraInfo extraInfo;
    /** 详情页推荐卡片，给用户快速决策用。 */
    private List<ShopRecommendation> recommendations = new ArrayList<>();
    /** 同品类或同商圈的关联店铺推荐。 */
    private List<ShopRelatedShop> relatedShops = new ArrayList<>();
}
