package com.hmdp.shopreview.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopReviewRewardMessage {
    private Long reviewId;
    private Long shopId;
    private Long userId;
    private Long triggerTime;
}
