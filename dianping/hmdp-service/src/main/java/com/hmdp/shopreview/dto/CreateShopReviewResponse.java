package com.hmdp.shopreview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateShopReviewResponse {
    private Long reviewId;
    private Boolean rewardEligible;
    private String message;
}
