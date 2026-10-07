package com.hmdp.shopreview.service.impl;

import com.hmdp.shopreview.domain.model.ShopReviewRewardMessage;
import com.hmdp.shopreview.dto.CreateShopReviewRequest;
import com.hmdp.shopreview.dto.CreateShopReviewResponse;
import com.hmdp.shopreview.service.ShopReviewRewardProcessor;
import com.hmdp.shopreview.service.ShopReviewRewardService;
import com.hmdp.shopreview.service.ShopReviewWriteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ShopReviewRewardServiceImpl implements ShopReviewRewardService {

    private final ShopReviewWriteService writeService;
    private final ShopReviewRewardProcessor rewardProcessor;

    public ShopReviewRewardServiceImpl(ShopReviewWriteService writeService,
                                       ShopReviewRewardProcessor rewardProcessor) {
        this.writeService = writeService;
        this.rewardProcessor = rewardProcessor;
    }

    @Override
    public CreateShopReviewResponse createReview(Long userId, CreateShopReviewRequest request) {
        Long reviewId = writeService.createReview(userId, request);
        log.info("shop review created, reviewId={}, shopId={}, userId={}", reviewId, request.getShopId(), userId);

        rewardProcessor.process(new ShopReviewRewardMessage(
                reviewId, request.getShopId(), userId, System.currentTimeMillis()));

        return new CreateShopReviewResponse(reviewId, true, "评价成功");
    }
}
