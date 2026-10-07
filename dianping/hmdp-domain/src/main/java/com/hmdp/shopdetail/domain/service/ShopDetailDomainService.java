package com.hmdp.shopdetail.domain.service;

import com.hmdp.shopdetail.domain.model.GuaranteeClaim;
import com.hmdp.shopdetail.domain.model.ShopDecisionTag;
import com.hmdp.shopdetail.domain.model.ShopDetailAggregate;
import com.hmdp.shopdetail.domain.model.ShopExtraInfo;
import com.hmdp.shopdetail.domain.model.ShopMarketingOffer;
import com.hmdp.shopdetail.domain.model.ShopRecommendation;
import com.hmdp.shopdetail.domain.model.ShopRelatedShop;
import com.hmdp.shopdetail.domain.model.ShopReview;
import com.hmdp.shopdetail.domain.repository.GuaranteeClaimRepository;
import com.hmdp.shopdetail.domain.repository.ShopDetailRepository;
import com.hmdp.shopdetail.domain.repository.ShopReviewRepository;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 店铺详情领域服务
 * <p>
 * 封装跨实体的核心业务逻辑，包括：
 * <ul>
 *   <li>聚合查询：组合多个仓储查询，构建完整的 {@link ShopDetailAggregate}</li>
 *   <li>数据丰富：合并决策标签、构建推荐卡片、周边店铺和扩展信息</li>
 *   <li>申诉管理：创建、更新、查询 {@link GuaranteeClaim}</li>
 * </ul>
 * 注意：该服务不感知任务链引擎，并行编排由应用层负责。
 * 如果你顺着商详主流程读代码，这个类就是“业务规则收口点”：
 * 查询节点只搬运原始事实，真正的标签合并、推荐卡片生成都在这里完成。
 * </p>
 */
@Service
public class ShopDetailDomainService {

    /** 店铺基础资料、营销活动、达人内容等查询入口。 */
    @Autowired(required = false)
    private ShopDetailRepository shopDetailRepository;

    /** 正式店铺评价查询入口。 */
    @Autowired(required = false)
    private ShopReviewRepository shopReviewRepository;

    /** 保障申诉持久化入口。 */
    @Autowired(required = false)
    private GuaranteeClaimRepository guaranteeClaimRepository;

    public ShopDetailDomainService() {
    }

    public ShopDetailDomainService(ShopDetailRepository shopDetailRepository,
                                   ShopReviewRepository shopReviewRepository,
                                   GuaranteeClaimRepository guaranteeClaimRepository) {
        this.shopDetailRepository = shopDetailRepository;
        this.shopReviewRepository = shopReviewRepository;
        this.guaranteeClaimRepository = guaranteeClaimRepository;
    }

    /**
     * 同步聚合查询店铺详情（不使用任务链引擎的降级路径）
     * <p>串行查询基础信息、评价、优惠券、探店博客，再调用 enrichShopDetail 丰富数据。</p>
     */
    public ShopDetailAggregate getShopDetail(Long shopId) {
        ShopDetailAggregate aggregate = shopDetailRepository.findBaseDetail(shopId);
        if (aggregate == null) {
            return new ShopDetailAggregate();
        }
        // 评价、优惠券、探店内容仍然来自各自仓储，领域服务负责统一聚合口径。
        aggregate.setReviewSummary(shopReviewRepository.summarize(shopId));
        aggregate.setReviews(shopReviewRepository.findByShopId(shopId, 10));
        aggregate.setVouchers(shopDetailRepository.findOffers(shopId));
        aggregate.setBlogs(shopDetailRepository.findVisitBlogs(shopId, 6));
        return enrichShopDetail(aggregate);
    }

    /** 创建申诉单，自动填充创建时间后持久化 */
    public GuaranteeClaim createClaim(GuaranteeClaim claim) {
        claim.setCreateTime(LocalDateTime.now());
        return guaranteeClaimRepository.save(claim);
    }

    /** 更新申诉单状态（补偿流引擎流转后回写） */
    public GuaranteeClaim updateClaim(GuaranteeClaim claim) {
        return guaranteeClaimRepository.save(claim);
    }

    /** 按申诉单号查询，不存在时返回 null */
    public GuaranteeClaim getClaim(String claimNo) {
        return guaranteeClaimRepository.findByClaimNo(claimNo).orElse(null);
    }

    /**
     * 丰富聚合根数据（任务链并行查询完成后调用）
     * <p>依次填充：决策标签、推荐卡片、周边店铺、扩展信息、保障计划。</p>
     * 这一步只依赖已经查到的事实数据，不再做额外远程编排，因此适合作为统一的“领域收口点”。
     */
    public ShopDetailAggregate enrichShopDetail(ShopDetailAggregate aggregate) {
        if (aggregate == null) {
            return new ShopDetailAggregate();
        }
        // 这些字段都属于“从已有事实推导出的展示结果”，放在一个出口统一生成，避免规则散落。
        aggregate.setTags(mergeDecisionTags(aggregate));
        aggregate.setRecommendations(buildRecommendations(aggregate));
        aggregate.setRelatedShops(buildRelatedShops(aggregate));
        aggregate.setExtraInfo(buildExtraInfo(aggregate));
        return aggregate;
    }

    /**
     * 合并基础标签、评价标签和博客标签。
     * 同名标签保留权重更高的一条，保证前端看到的是当前最可信的决策信号。
     */
    private List<ShopDecisionTag> mergeDecisionTags(ShopDetailAggregate aggregate) {
        List<ShopDecisionTag> tags = new ArrayList<>();
        if (aggregate.getTags() != null) {
            tags.addAll(aggregate.getTags());
        }
        if (aggregate.getReviewSummary() != null && aggregate.getReviewSummary().getTopTags() != null) {
            aggregate.getReviewSummary().getTopTags().stream()
                    .filter(StrUtil::isNotBlank)
                    .limit(3)
                    .forEach(tag -> tags.add(new ShopDecisionTag("review_" + tag.hashCode(), tag, "review", 85)));
        }
        if (aggregate.getBlogs() != null && aggregate.getBlogs().stream().anyMatch(blog -> StrUtil.containsAnyIgnoreCase(blog.getContent(), "约会", "氛围", "环境"))) {
            tags.add(new ShopDecisionTag("blog_date", "适合约会", "blog", 80));
        }
        return tags.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(ShopDecisionTag::getName, tag -> tag, (left, right) -> left.getWeight() >= right.getWeight() ? left : right),
                        map -> new ArrayList<>(map.values())
                ));
    }

    /** 推荐卡片是详情页决策区的核心输出，按“套餐 -> 理由 -> 榜单”顺序组织。 */
    private List<ShopRecommendation> buildRecommendations(ShopDetailAggregate aggregate) {
        List<ShopRecommendation> recommendations = new ArrayList<>();
        List<ShopMarketingOffer> offers = aggregate.getVouchers() == null ? Collections.<ShopMarketingOffer>emptyList() : aggregate.getVouchers();
        if (!offers.isEmpty()) {
            recommendations.add(buildSignatureDishCard(aggregate, offers));
        }
        if (aggregate.getReviewSummary() != null) {
            recommendations.add(buildRecommendReasonCard(aggregate));
            recommendations.add(buildRankingCard(aggregate));
        }
        return recommendations;
    }

    /** 周边推荐只作为兜底内容，不覆盖当前店铺本身。 */
    private List<ShopRelatedShop> buildRelatedShops(ShopDetailAggregate aggregate) {
        if (aggregate.getTypeId() == null) {
            return Collections.emptyList();
        }
        return shopDetailRepository.findNearbyShops(aggregate.getTypeId(), aggregate.getX(), aggregate.getY(), 1).stream()
                .filter(shop -> shop.getShopId() != null && !shop.getShopId().equals(aggregate.getShopId()))
                .limit(3)
                .map(shop -> new ShopRelatedShop(
                        shop.getShopId(),
                        shop.getShopName(),
                        shop.getArea(),
                        shop.getAvgPrice(),
                        shop.getScore(),
                        shop.getDistance(),
                        buildRelatedReason(aggregate, shop)
                ))
                .collect(Collectors.toList());
    }

    /** 组装“到店须知”类信息，把基础字段转换成用户能直接理解的文案。 */
    private ShopExtraInfo buildExtraInfo(ShopDetailAggregate aggregate) {
        ShopExtraInfo extraInfo = new ShopExtraInfo();
        extraInfo.setFullAddress(aggregate.getAddress());
        extraInfo.setTrafficTips(buildTrafficTips(aggregate));
        extraInfo.setAlbum(splitCsv(aggregate.getImages()));
        extraInfo.setServiceFacilities(buildFacilities(aggregate));
        extraInfo.setBusinessHoursNotice(StrUtil.isBlank(aggregate.getOpenHours()) ? "到店前建议电话确认营业时间" : "营业时间：" + aggregate.getOpenHours());
        return extraInfo;
    }

    private String buildTrafficTips(ShopDetailAggregate aggregate) {
        if (aggregate.getDistance() == null) {
            return "建议优先地铁或网约车到店，导航至" + safeValue(aggregate.getAddress(), aggregate.getShopName());
        }
        if (aggregate.getDistance() <= 800) {
            return "距离地铁 2 号线步行约 5 分钟，可直接导航至" + safeValue(aggregate.getAddress(), aggregate.getShopName());
        }
        return "距离稍远，建议打车或骑行前往，导航至" + safeValue(aggregate.getAddress(), aggregate.getShopName());
    }

    private List<String> buildFacilities(ShopDetailAggregate aggregate) {
        List<String> facilities = new ArrayList<>();
        if (aggregate.getAvgPrice() != null && aggregate.getAvgPrice() >= 100) {
            facilities.add("适合聚餐");
        } else {
            facilities.add("适合轻量到店");
        }
        if (aggregate.getBlogs() != null && !aggregate.getBlogs().isEmpty()) {
            facilities.add("达人探店丰富");
        }
        facilities.add("支持在线查看优惠");
        return facilities;
    }

    private String offerReason(ShopDetailAggregate aggregate, ShopMarketingOffer offer) {
        if (aggregate.getReviewSummary() != null && aggregate.getReviewSummary().getRecommendationRate() != null
                && aggregate.getReviewSummary().getRecommendationRate() >= 85) {
            return "评价口碑稳定，适合首次到店";
        }
        return "适合先低成本体验这家店";
    }

    private String offerHighlight(ShopMarketingOffer offer) {
        if (offer.getPayValue() != null && offer.getActualValue() != null) {
            return "券后 " + offer.getPayValue() + " 元，可抵 " + offer.getActualValue() + " 元";
        }
        return safeValue(offer.getSubTitle(), offer.getRules());
    }

    private String buildRelatedReason(ShopDetailAggregate current, ShopDetailAggregate related) {
        if (StrUtil.isNotBlank(current.getArea()) && current.getArea().equals(related.getArea())) {
            return "同商圈口碑店";
        }
        return "同品类附近热门店";
    }

    private ShopRecommendation buildSignatureDishCard(ShopDetailAggregate aggregate, List<ShopMarketingOffer> offers) {
        ShopMarketingOffer primaryOffer = offers.get(0);
        ShopRecommendation recommendation = new ShopRecommendation();
        recommendation.setType("signatureDish");
        recommendation.setTitle(primaryOffer.getTitle());
        recommendation.setReason(offerReason(aggregate, primaryOffer));
        recommendation.setHighlight(offerHighlight(primaryOffer));
        recommendation.setBadge("招牌必点");
        recommendation.setTags(Arrays.asList("高人气", "到店常点"));
        recommendation.setItems(buildSignatureDishItems(offers, aggregate));
        return recommendation;
    }

    private ShopRecommendation buildRecommendReasonCard(ShopDetailAggregate aggregate) {
        ShopRecommendation recommendation = new ShopRecommendation();
        recommendation.setType("recommendReason");
        recommendation.setTitle("为什么推荐这家店");
        recommendation.setReason(buildRecommendReasonText(aggregate));
        recommendation.setHighlight("结合评价、达人内容和价格带综合判断");
        recommendation.setBadge("推荐理由");
        recommendation.setTags(buildRecommendReasonTags(aggregate));
        recommendation.setItems(buildRecommendReasonItems(aggregate));
        return recommendation;
    }

    private ShopRecommendation buildRankingCard(ShopDetailAggregate aggregate) {
        ShopRecommendation recommendation = new ShopRecommendation();
        recommendation.setType("rankingCard");
        recommendation.setTitle("店铺口碑榜单");
        recommendation.setReason("当前店铺在口碑、评价量和价格带上表现均衡");
        recommendation.setHighlight(buildRankingHighlight(aggregate));
        recommendation.setBadge("口碑榜");
        recommendation.setTags(buildRankingTags(aggregate));
        recommendation.setItems(buildRankingItems(aggregate));
        return recommendation;
    }

    private List<String> buildSignatureDishItems(List<ShopMarketingOffer> offers, ShopDetailAggregate aggregate) {
        List<String> items = new ArrayList<>();
        offers.stream()
                .limit(2)
                .map(ShopMarketingOffer::getTitle)
                .filter(StrUtil::isNotBlank)
                .forEach(items::add);
        if (aggregate.getBlogs() != null && aggregate.getBlogs().stream().anyMatch(blog -> StrUtil.contains(blog.getContent(), "黑松露"))) {
            items.add("黑松露煎和牛");
        }
        if (aggregate.getReviewSummary() != null && aggregate.getReviewSummary().getTopTags() != null
                && aggregate.getReviewSummary().getTopTags().stream().anyMatch(tag -> StrUtil.contains(tag, "适合约会"))) {
            items.add("招牌漏奶华");
        }
        if (items.isEmpty()) {
            items.add("招牌双人餐");
        }
        return items.stream().distinct().limit(3).collect(Collectors.toList());
    }

    private String buildRecommendReasonText(ShopDetailAggregate aggregate) {
        Integer recommendationRate = aggregate.getReviewSummary() == null ? null : aggregate.getReviewSummary().getRecommendationRate();
        if (recommendationRate != null && recommendationRate >= 90) {
            return "评价推荐率高，适合第一次来这家店时直接下决定。";
        }
        if (aggregate.getAvgPrice() != null && aggregate.getAvgPrice() <= 100) {
            return "价格门槛不高，适合朋友小聚或临时约饭。";
        }
        return "环境、评价和优惠信息比较均衡，决策成本低。";
    }

    private List<String> buildRecommendReasonTags(ShopDetailAggregate aggregate) {
        List<String> tags = new ArrayList<>();
        if (aggregate.getReviewSummary() != null && aggregate.getReviewSummary().getRecommendationRate() != null) {
            tags.add("高推荐率");
        }
        if (aggregate.getAvgPrice() != null && aggregate.getAvgPrice() <= 100) {
            tags.add("人均友好");
        }
        if (aggregate.getBlogs() != null && !aggregate.getBlogs().isEmpty()) {
            tags.add("达人常探");
        }
        return tags;
    }

    private List<String> buildRecommendReasonItems(ShopDetailAggregate aggregate) {
        List<String> items = new ArrayList<>();
        if (aggregate.getReviewSummary() != null && aggregate.getReviewSummary().getTopTags() != null) {
            aggregate.getReviewSummary().getTopTags().stream().limit(2).forEach(items::add);
        }
        if (aggregate.getBlogs() != null && !aggregate.getBlogs().isEmpty()) {
            items.add("达人内容可辅助判断环境和出品");
        }
        if (items.isEmpty()) {
            items.add("基础信息完整");
        }
        return items;
    }

    private String buildRankingHighlight(ShopDetailAggregate aggregate) {
        String area = safeValue(aggregate.getArea(), "附近");
        String score = aggregate.getScore() == null ? "-" : String.format("%.1f", aggregate.getScore() / 10.0);
        return area + "商圈评分约" + score + "，评价量" + (aggregate.getComments() == null ? 0 : aggregate.getComments()) + "条";
    }

    private List<String> buildRankingTags(ShopDetailAggregate aggregate) {
        List<String> tags = new ArrayList<>();
        tags.add("商圈口碑");
        if (aggregate.getComments() != null && aggregate.getComments() >= 20) {
            tags.add("评价量稳定");
        }
        if (aggregate.getScore() != null && aggregate.getScore() >= 45) {
            tags.add("评分靠前");
        }
        return tags;
    }

    private List<String> buildRankingItems(ShopDetailAggregate aggregate) {
        List<String> items = new ArrayList<>();
        items.add("人均 " + safeValue(aggregate.getAvgPrice() == null ? null : String.valueOf(aggregate.getAvgPrice()), "-") + " 元");
        items.add("营业时间 " + safeValue(aggregate.getOpenHours(), "待确认"));
        if (aggregate.getDistance() != null) {
            items.add("距离约 " + String.format("%.0f", aggregate.getDistance()) + " 米");
        }
        return items;
    }

    private List<String> splitCsv(String value) {
        if (StrUtil.isBlank(value)) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }

    private String safeValue(String preferred, String fallback) {
        return StrUtil.isBlank(preferred) ? fallback : preferred;
    }
}
