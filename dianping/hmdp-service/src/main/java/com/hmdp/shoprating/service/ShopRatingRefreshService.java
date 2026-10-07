package com.hmdp.shoprating.service;

public interface ShopRatingRefreshService {

    void triggerFromBlog(Long shopId);

    void triggerIfExpired(Long shopId);
}
