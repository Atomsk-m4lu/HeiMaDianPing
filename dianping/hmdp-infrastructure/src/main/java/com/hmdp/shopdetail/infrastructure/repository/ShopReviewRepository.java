package com.hmdp.shopdetail.infrastructure.repository;

import cn.hutool.core.util.StrUtil;
import com.hmdp.entity.User;
import com.hmdp.service.compat.LegacyUserQueryService;
import com.hmdp.shopdetail.domain.model.ShopReview;
import com.hmdp.shopdetail.domain.model.ShopReviewSummary;
import com.hmdp.shopdetail.infrastructure.persistence.mapper.ShopDetailReviewMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 店铺评价仓储实现（基础设施层）
 * <p>
 * 通过 MyBatis Plus 查询 tb_shop_review 表，
 * 并通过 {@link LegacyUserQueryService} 补充评价者的昵称和头像。
 * summarize 方法取最近 100 条评价在内存中计算汇总统计。
 * </p>
 */
@Repository
public class ShopReviewRepository implements com.hmdp.shopdetail.domain.repository.ShopReviewRepository {

    @Resource
    private ShopDetailReviewMapper shopDetailReviewMapper;

    @Resource
    private LegacyUserQueryService legacyUserQueryService;

    @Override
    public List<ShopReview> findByShopId(Long shopId, int limit) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.hmdp.shopdetail.infrastructure.persistence.dataobject.ShopReview> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.eq("shop_id", shopId).orderByDesc("create_time").last("limit " + limit);
        List<com.hmdp.shopdetail.infrastructure.persistence.dataobject.ShopReview> reviewDOS = shopDetailReviewMapper.selectList(wrapper);
        return reviewDOS.stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public ShopReviewSummary summarize(Long shopId) {
        List<ShopReview> reviews = findByShopId(shopId, 100);
        ShopReviewSummary summary = new ShopReviewSummary();
        summary.setTotalReviews(reviews.size());
        if (reviews.isEmpty()) {
            summary.setAverageScore(0);
            summary.setRecommendationRate(0);
            summary.setTopTags(Collections.emptyList());
            return summary;
        }
        int scoreSum = reviews.stream().map(ShopReview::getOverallScore).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        summary.setAverageScore(scoreSum / reviews.size());
        long recommended = reviews.stream().filter(review -> review.getOverallScore() != null && review.getOverallScore() >= 40).count();
        summary.setRecommendationRate((int) (recommended * 100 / reviews.size()));
        summary.setTopTags(reviews.stream()
                .flatMap(review -> review.getTags() == null ? java.util.stream.Stream.<String>empty() : review.getTags().stream())
                .filter(StrUtil::isNotBlank)
                .distinct()
                .limit(5)
                .collect(Collectors.toList()));
        return summary;
    }

    private ShopReview toDomain(com.hmdp.shopdetail.infrastructure.persistence.dataobject.ShopReview dataObject) {
        ShopReview review = new ShopReview();
        review.setId(dataObject.getId());
        review.setShopId(dataObject.getShopId());
        review.setUserId(dataObject.getUserId());
        review.setOverallScore(dataObject.getOverallScore());
        review.setTasteScore(dataObject.getTasteScore());
        review.setEnvironmentScore(dataObject.getEnvironmentScore());
        review.setServiceScore(dataObject.getServiceScore());
        review.setContent(dataObject.getContent());
        review.setTags(StrUtil.isBlank(dataObject.getTags()) ? Collections.<String>emptyList() : Arrays.asList(dataObject.getTags().split(",")));
        review.setCreateTime(dataObject.getCreateTime());
        User user = legacyUserQueryService.getUserById(dataObject.getUserId());
        if (user != null) {
            review.setUserName(user.getNickName());
            review.setUserIcon(user.getIcon());
        }
        return review;
    }
}
