package com.hmdp.shopdetail.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 周边相关店铺值对象
 * <p>
 * 详情页底部"附近同类店"区域的数据，展示同商圈或同品类的推荐店铺。
 * 由 {@link com.hmdp.shopdetail.domain.service.ShopDetailDomainService#buildRelatedShops} 查询附近店铺后筛选构建。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopRelatedShop {

    private Long shopId;
    private String shopName;
    private String area;
    private Long avgPrice;
    private Integer score;
    private Double distance;
    private String recommendationReason;
}
