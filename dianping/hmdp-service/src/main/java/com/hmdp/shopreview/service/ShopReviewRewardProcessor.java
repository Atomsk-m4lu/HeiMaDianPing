package com.hmdp.shopreview.service;

import cn.hutool.core.util.StrUtil;
import com.hmdp.shopreview.domain.model.ShopReviewRewardMessage;
import org.springframework.stereotype.Service;

@Service
public class ShopReviewRewardProcessor {

    public static final int REWARD_POINTS = 10;

    private final ShopReviewQueryService reviewQueryService;
    private final ShopReviewRewardRecordService rewardRecordService;
    private final UserCreditService userCreditService;

    public ShopReviewRewardProcessor(ShopReviewQueryService reviewQueryService,
                                     ShopReviewRewardRecordService rewardRecordService,
                                     UserCreditService userCreditService) {
        this.reviewQueryService = reviewQueryService;
        this.rewardRecordService = rewardRecordService;
        this.userCreditService = userCreditService;
    }

    public void process(ShopReviewRewardMessage message) {
        ShopReviewSnapshot snapshot = reviewQueryService.getById(message.getReviewId());
        if (!isRewardEligible(snapshot)) {
            return;
        }
        Long recordId = rewardRecordService.createPendingRecord(snapshot, REWARD_POINTS);
        if (recordId == null) {
            return;
        }
        userCreditService.addCredits(snapshot.getUserId(), REWARD_POINTS);
        rewardRecordService.markSuccess(recordId);
    }

    private boolean isRewardEligible(ShopReviewSnapshot snapshot) {
        return snapshot != null
                && snapshot.getOverallScore() != null
                && StrUtil.isNotBlank(snapshot.getContent());
    }
}
