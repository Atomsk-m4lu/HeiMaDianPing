package com.hmdp.shopreview.service;

import com.hmdp.shopreview.domain.model.ReviewRewardStatus;
import com.hmdp.shopreview.domain.model.ShopReviewRewardMessage;
import com.hmdp.shopreview.entity.ShopReviewRewardRecord;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopReviewRewardProcessorTest {

    @Test
    void should_insert_reward_record_add_credits_and_mark_success() {
        ShopReviewQueryService reviewQueryService = mock(ShopReviewQueryService.class);
        ShopReviewRewardRecordService rewardRecordService = mock(ShopReviewRewardRecordService.class);
        UserCreditService userCreditService = mock(UserCreditService.class);
        ShopReviewRewardProcessor processor = new ShopReviewRewardProcessor(reviewQueryService, rewardRecordService, userCreditService);

        ShopReviewSnapshot snapshot = new ShopReviewSnapshot(8L, 2L, 5L, 46, "值得再来");
        when(reviewQueryService.getById(8L)).thenReturn(snapshot);
        when(rewardRecordService.createPendingRecord(snapshot, 10)).thenReturn(21L);

        processor.process(new ShopReviewRewardMessage(8L, 2L, 5L, System.currentTimeMillis()));

        verify(userCreditService).addCredits(5L, 10);
        verify(rewardRecordService).markSuccess(21L);
    }

    @Test
    void should_skip_credit_award_when_reward_record_already_exists() {
        ShopReviewQueryService reviewQueryService = mock(ShopReviewQueryService.class);
        ShopReviewRewardRecordService rewardRecordService = mock(ShopReviewRewardRecordService.class);
        UserCreditService userCreditService = mock(UserCreditService.class);
        ShopReviewRewardProcessor processor = new ShopReviewRewardProcessor(reviewQueryService, rewardRecordService, userCreditService);

        ShopReviewSnapshot snapshot = new ShopReviewSnapshot(8L, 2L, 5L, 46, "值得再来");
        when(reviewQueryService.getById(8L)).thenReturn(snapshot);
        when(rewardRecordService.createPendingRecord(snapshot, 10)).thenReturn(null);

        processor.process(new ShopReviewRewardMessage(8L, 2L, 5L, System.currentTimeMillis()));

        verify(userCreditService, never()).addCredits(any(Long.class), eq(10));
        verify(rewardRecordService, never()).markSuccess(any(Long.class));
    }
}
