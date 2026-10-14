package com.hmdp.shopdetail.infrastructure.repository;

import cn.hutool.core.util.StrUtil;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.shopdetail.domain.model.ShopReview;
import com.hmdp.shopdetail.domain.model.ShopReviewSummary;
import com.hmdp.shopdetail.infrastructure.persistence.mapper.ShopDetailReviewMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class ShopReviewRepository implements com.hmdp.shopdetail.domain.repository.ShopReviewRepository {

    @Resource
    private ShopDetailReviewMapper shopDetailReviewMapper;

    @Resource
    private IUserService userService;

    @Override
    public List<ShopReview> findByShopId(Long shopId, int limit) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.hmdp.shopdetail.infrastructure.persistence.dataobject.ShopReview> wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.eq("shop_id", shopId).orderByDesc("create_time").last("limit " + limit);
        List<com.hmdp.shopdetail.infrastructure.persistence.dataobject.ShopReview> reviewDOS = shopDetailReviewMapper.selectList(wrapper);

        Set<Long> userIds = reviewDOS.stream()
                .map(com.hmdp.shopdetail.infrastructure.persistence.dataobject.ShopReview::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        return reviewDOS.stream().map(dos -> toDomain(dos, userMap)).collect(Collectors.toList());
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

    private ShopReview toDomain(com.hmdp.shopdetail.infrastructure.persistence.dataobject.ShopReview dataObject, Map<Long, User> userMap) {
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
        User user = userMap.get(dataObject.getUserId());
        if (user != null) {
            review.setUserName(user.getNickName());
            review.setUserIcon(user.getIcon());
        }
        return review;
    }
}
