package com.hmdp.shopreview.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.mapper.ShopReviewMapper;
import com.hmdp.shopreview.dto.CreateShopReviewRequest;
import com.hmdp.shopreview.entity.ShopReview;
import com.hmdp.shopreview.service.ShopReviewWriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopReviewWriteServiceImpl implements ShopReviewWriteService {

    private final ShopReviewMapper shopReviewMapper;

    public ShopReviewWriteServiceImpl(ShopReviewMapper shopReviewMapper) {
        this.shopReviewMapper = shopReviewMapper;
    }

    @Override
    @Transactional
    public Long createReview(Long userId, CreateShopReviewRequest request) {
        ShopReview review = new ShopReview();
        review.setShopId(request.getShopId());
        review.setUserId(userId);
        review.setOverallScore(request.getOverallScore());
        review.setTasteScore(request.getTasteScore());
        review.setEnvironmentScore(request.getEnvironmentScore());
        review.setServiceScore(request.getServiceScore());
        review.setContent(StrUtil.trim(request.getContent()));
        review.setTags(StrUtil.trim(request.getTags()));
        shopReviewMapper.insert(review);
        return review.getId();
    }
}
