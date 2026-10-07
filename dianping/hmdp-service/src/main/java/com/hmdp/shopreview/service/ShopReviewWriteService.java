package com.hmdp.shopreview.service;

import com.hmdp.shopreview.dto.CreateShopReviewRequest;

public interface ShopReviewWriteService {
    Long createReview(Long userId, CreateShopReviewRequest request);
}
