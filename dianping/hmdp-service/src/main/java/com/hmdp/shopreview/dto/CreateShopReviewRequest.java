package com.hmdp.shopreview.dto;

import lombok.Data;

@Data
public class CreateShopReviewRequest {
    private Long shopId;
    private Integer overallScore;
    private Integer tasteScore;
    private Integer environmentScore;
    private Integer serviceScore;
    private String content;
    private String tags;
}
