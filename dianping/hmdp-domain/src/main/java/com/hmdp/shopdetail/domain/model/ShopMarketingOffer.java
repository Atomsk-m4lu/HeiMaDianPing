package com.hmdp.shopdetail.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 营销优惠值对象（优惠券/套餐）
 * <p>
 * 描述店铺当前可用的优惠活动，包含券面值、实际抵扣金额、有效期和库存。
 * 对应数据库中的 Voucher 表，通过适配器从遗留服务转换而来。
 * </p>
 */
@Data
public class ShopMarketingOffer {

    private Long id;
    private Long shopId;
    private String title;
    private String subTitle;
    private String rules;
    private Long payValue;
    private Long actualValue;
    private Integer type;
    private Integer status;
    private Integer stock;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
}
