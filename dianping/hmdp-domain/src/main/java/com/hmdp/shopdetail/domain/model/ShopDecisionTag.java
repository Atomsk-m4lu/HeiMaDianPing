package com.hmdp.shopdetail.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 决策标签值对象
 * <p>
 * 展示在详情页顶部的辅助决策标签，如"评分 4.8"、"适合约会"等。
 * 标签来源多样（base=基础数据、review=评价、blog=探店内容），
 * 由 {@link com.hmdp.shopdetail.domain.service.ShopDetailDomainService} 合并去重后填入聚合根。
 * weight 越高，展示优先级越高。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopDecisionTag {

    private String code;
    private String name;
    private String source;
    private Integer weight;

    public ShopDecisionTag(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
