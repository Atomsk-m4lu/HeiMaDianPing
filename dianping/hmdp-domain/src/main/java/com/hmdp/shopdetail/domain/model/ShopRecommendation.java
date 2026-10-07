package com.hmdp.shopdetail.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 推荐卡片值对象
 * <p>
 * 详情页"推荐理由"区域的卡片数据，目前有三种类型：
 * <ul>
 *   <li>signatureDish：招牌必点，基于优惠券和探店内容生成</li>
 *   <li>recommendReason：推荐理由，基于评价和价格带生成</li>
 *   <li>rankingCard：口碑榜单，基于评分和评价量生成</li>
 * </ul>
 * 由 {@link com.hmdp.shopdetail.domain.service.ShopDetailDomainService} 构建。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopRecommendation {

    private String type;
    private String title;
    private String reason;
    private String highlight;
    private String badge;
    private List<String> tags = new ArrayList<>();
    private List<String> items = new ArrayList<>();

    public ShopRecommendation(String type, String title, String reason, String highlight) {
        this.type = type;
        this.title = title;
        this.reason = reason;
        this.highlight = highlight;
    }
}
