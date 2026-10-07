package com.hmdp.service.compat;

import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;

import java.util.List;

public interface LegacyShopQueryService {

    Shop getShopById(Long shopId);

    List<Shop> queryNearbyShops(Long typeId, Double x, Double y, int current);

    List<Voucher> getVouchersByShopId(Long shopId);
}
