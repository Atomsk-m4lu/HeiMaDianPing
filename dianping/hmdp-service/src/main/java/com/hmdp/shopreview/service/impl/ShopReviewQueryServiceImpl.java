package com.hmdp.shopreview.service.impl;

import com.hmdp.mapper.ShopReviewMapper;
import com.hmdp.shopreview.entity.ShopReview;
import com.hmdp.shopreview.service.ShopReviewQueryService;
import com.hmdp.shopreview.service.ShopReviewSnapshot;
import org.springframework.stereotype.Service;

@Service
public class ShopReviewQueryServiceImpl implements ShopReviewQueryService {

    private final ShopReviewMapper shopReviewMapper;

    public ShopReviewQueryServiceImpl(ShopReviewMapper shopReviewMapper) {
        this.shopReviewMapper = shopReviewMapper;
    }

    @Override
    public ShopReviewSnapshot getById(Long reviewId) {
        ShopReview review = shopReviewMapper.selectById(reviewId);
        if (review == null) {
            return null;
        }
        return new ShopReviewSnapshot(review.getId(), review.getShopId(), review.getUserId(),
                review.getOverallScore(), review.getContent());
    }
}
