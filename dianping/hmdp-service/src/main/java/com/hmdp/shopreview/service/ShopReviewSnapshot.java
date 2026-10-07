package com.hmdp.shopreview.service;

import lombok.Data;

@Data
public class ShopReviewSnapshot {
    private final Long reviewId;
    private final Long shopId;
    private final Long userId;
    private final Integer overallScore;
    private final String content;

    public ShopReviewSnapshot(Long reviewId, Long shopId, Long userId, Integer overallScore, String content) {
        this.reviewId = reviewId;
        this.shopId = shopId;
        this.userId = userId;
        this.overallScore = overallScore;
        this.content = content;
    }
}
