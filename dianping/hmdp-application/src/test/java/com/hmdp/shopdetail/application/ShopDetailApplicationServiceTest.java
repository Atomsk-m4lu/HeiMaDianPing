package com.hmdp.shopdetail.application;

import com.hmdp.shopdetail.application.service.ShopDetailApplicationService;
import com.hmdp.shopdetail.domain.model.ShopDetailAggregate;
import com.hmdp.shopdetail.domain.service.ShopDetailDomainService;
import com.hmdp.shoprating.service.ShopRatingRefreshService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ShopDetailApplicationServiceTest {

    @Test
    void should_trigger_rating_refresh_check_after_loading_detail() {
        ShopDetailAggregate aggregate = new ShopDetailAggregate();
        aggregate.setShopId(1L);
        aggregate.setShopName("103茶餐厅");

        ShopDetailDomainService domainService = new ShopDetailDomainService() {
            @Override
            public ShopDetailAggregate getShopDetail(Long shopId) {
                return aggregate;
            }
        };

        ShopRatingRefreshService refreshService = Mockito.mock(ShopRatingRefreshService.class);

        ShopDetailApplicationService applicationService =
                new ShopDetailApplicationService(domainService, refreshService);

        assertEquals("103茶餐厅", applicationService.getShopDetail(1L).getShopName());
        verify(refreshService).triggerIfExpired(1L);
    }

    @Test
    void should_not_trigger_refresh_when_detail_is_missing() {
        ShopDetailDomainService domainService = new ShopDetailDomainService() {
            @Override
            public ShopDetailAggregate getShopDetail(Long shopId) {
                return null;
            }
        };

        ShopRatingRefreshService refreshService = Mockito.mock(ShopRatingRefreshService.class);

        ShopDetailApplicationService applicationService =
                new ShopDetailApplicationService(domainService, refreshService);

        applicationService.getShopDetail(1L);

        verify(refreshService, never()).triggerIfExpired(1L);
    }
}
