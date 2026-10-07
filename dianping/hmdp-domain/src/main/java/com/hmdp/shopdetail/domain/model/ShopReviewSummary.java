package com.hmdp.shopdetail.domain.model;

import lombok.Data;

import java.util.List;

/**
 * 店铺评价汇总值对象
 * <p>
 * 对店铺所有评价的统计摘要，包含总评价数、平均分、推荐率和高频标签。
 * 由 {@link com.hmdp.shopdetail.domain.repository.ShopReviewRepository#summarize(Long)} 计算生成，
 * 用于详情页评价区域的概览展示。
 * </p>
 */
@Data
public class ShopReviewSummary {

    private Integer totalReviews;
    private Integer averageScore;
    private Integer recommendationRate;
    private List<String> topTags;
}
