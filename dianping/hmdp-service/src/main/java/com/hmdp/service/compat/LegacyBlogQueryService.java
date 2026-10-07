package com.hmdp.service.compat;

import com.hmdp.entity.Blog;

import java.util.List;

public interface LegacyBlogQueryService {

    List<Blog> getBlogsByShopId(Long shopId, int limit);
}
