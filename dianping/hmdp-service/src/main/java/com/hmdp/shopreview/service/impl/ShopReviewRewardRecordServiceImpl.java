package com.hmdp.shopreview.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.mapper.ShopReviewRewardRecordMapper;
import com.hmdp.shopreview.domain.model.ReviewRewardStatus;
import com.hmdp.shopreview.entity.ShopReviewRewardRecord;
import com.hmdp.shopreview.service.ShopReviewRewardRecordService;
import com.hmdp.shopreview.service.ShopReviewSnapshot;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ShopReviewRewardRecordServiceImpl implements ShopReviewRewardRecordService {

    private static final String REWARD_TYPE = "REVIEW_CREDIT";

    private final ShopReviewRewardRecordMapper rewardRecordMapper;

    public ShopReviewRewardRecordServiceImpl(ShopReviewRewardRecordMapper rewardRecordMapper) {
        this.rewardRecordMapper = rewardRecordMapper;
    }

    @Override
    public Long createPendingRecord(ShopReviewSnapshot snapshot, int rewardPoints) {
        ShopReviewRewardRecord record = new ShopReviewRewardRecord();
        record.setReviewId(snapshot.getReviewId());
        record.setShopId(snapshot.getShopId());
        record.setUserId(snapshot.getUserId());
        record.setRewardPoints(rewardPoints);
        record.setRewardType(REWARD_TYPE);
        record.setStatus(ReviewRewardStatus.PENDING);
        try {
            rewardRecordMapper.insert(record);
            return record.getId();
        } catch (DuplicateKeyException e) {
            return null;
        }
    }

    @Override
    public void markSuccess(Long recordId) {
        rewardRecordMapper.update(null, new UpdateWrapper<ShopReviewRewardRecord>()
                .eq("id", recordId)
                .set("status", ReviewRewardStatus.SUCCESS));
    }
}
