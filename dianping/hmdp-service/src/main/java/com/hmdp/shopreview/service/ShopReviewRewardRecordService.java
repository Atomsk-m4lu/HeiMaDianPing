package com.hmdp.shopreview.service;

public interface ShopReviewRewardRecordService {
    Long createPendingRecord(ShopReviewSnapshot snapshot, int rewardPoints);

    void markSuccess(Long recordId);
}
