package com.hmdp.shopdetail.facade.impl;

import com.hmdp.shopdetail.application.service.ShopDetailApplicationService;
import com.hmdp.shopdetail.domain.model.GuaranteeClaim;
import com.hmdp.shopdetail.domain.model.ShopDetailAggregate;
import com.hmdp.shopdetail.facade.ShopDetailFacade;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 店铺详情门面实现
 * <p>
 * 纯委托实现，将所有调用转发给 {@link ShopDetailApplicationService}，不包含任何业务逻辑。
 * 它存在的意义是给外部一个稳定入口，方便后续替换应用层实现而不影响 controller / job / listener。
 * </p>
 */
@Service
public class ShopDetailFacadeImpl implements ShopDetailFacade {

    @Resource
    private ShopDetailApplicationService shopDetailApplicationService;

    @Override
    public ShopDetailAggregate getShopDetail(Long shopId) {
        return shopDetailApplicationService.getShopDetail(shopId);
    }

    @Override
    public GuaranteeClaim submitGuaranteeClaim(GuaranteeClaim claim) {
        return shopDetailApplicationService.submitGuaranteeClaim(claim);
    }

    @Override
    public GuaranteeClaim retryGuaranteeClaim(String claimNo) {
        return shopDetailApplicationService.retryGuaranteeClaim(claimNo);
    }

    @Override
    public GuaranteeClaim getGuaranteeClaim(String claimNo) {
        return shopDetailApplicationService.getGuaranteeClaim(claimNo);
    }
}
