package com.hmdp.shopdetail.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 店铺扩展信息值对象
 * <p>
 * 包含详情页"更多信息"区域的内容：完整地址、交通提示、相册图片列表、服务设施和营业时间说明。
 * 由 {@link com.hmdp.shopdetail.domain.service.ShopDetailDomainService#buildExtraInfo} 根据聚合根数据动态构建。
 * </p>
 */
@Data
public class ShopExtraInfo {

    private String fullAddress;
    private String trafficTips;
    private List<String> album = new ArrayList<>();
    private List<String> serviceFacilities = new ArrayList<>();
    private String businessHoursNotice;
}
