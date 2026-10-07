package com.hmdp.shopreview.service;

import com.hmdp.shopreview.dto.CreateShopReviewRequest;
import com.hmdp.shopreview.dto.CreateShopReviewResponse;

public interface ShopReviewRewardService {
    CreateShopReviewResponse createReview(Long userId, CreateShopReviewRequest request);
}
