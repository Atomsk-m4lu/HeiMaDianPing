package com.hmdp.shopdetail.domain.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺评价实体
 * <p>
 * 记录单条用户评价，包含评分（总分、口味、环境、服务）、评价内容和标签。
 * 由 {@link com.hmdp.shopdetail.domain.repository.ShopReviewRepository} 查询，
 * 聚合到 {@link ShopDetailAggregate#getReviews()} 中展示。
 * </p>
 */
@Data
public class ShopReview {

    private Long id;
    private Long shopId;
    private Long userId;
    private String userName;
    private String userIcon;
    private Integer overallScore;
    private Integer tasteScore;
    private Integer environmentScore;
    private Integer serviceScore;
    private String content;
    private List<String> tags;
    private LocalDateTime createTime;
}
