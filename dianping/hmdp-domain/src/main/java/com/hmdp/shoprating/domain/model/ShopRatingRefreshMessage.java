package com.hmdp.shoprating.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopRatingRefreshMessage {
    private Long shopId;
    private ShopRatingTriggerSource triggerSource;
    private long triggerTime;
}
