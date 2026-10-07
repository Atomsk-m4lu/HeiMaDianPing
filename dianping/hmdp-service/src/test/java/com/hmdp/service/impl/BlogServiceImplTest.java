package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.service.IFollowService;
import com.hmdp.shoprating.service.ShopRatingRefreshService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlogServiceImplTest {

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void should_trigger_rating_refresh_when_blog_belongs_to_shop() {
        UserDTO user = new UserDTO();
        user.setId(8L);
        UserHolder.saveUser(user);

        ShopRatingRefreshService refreshService = Mockito.mock(ShopRatingRefreshService.class);
        IFollowService followService = Mockito.mock(IFollowService.class);
        QueryChainWrapper<Follow> query = Mockito.mock(QueryChainWrapper.class);
        when(followService.query()).thenReturn(query);
        when(query.eq("follow_user_id", 8L)).thenReturn(query);
        when(query.list()).thenReturn(Collections.emptyList());

        Blog blog = blogWithId(11L, 3L);
        BlogServiceImpl service = new BlogServiceImpl() {
            @Override
            public boolean save(Blog entity) {
                return true;
            }
        };
        ReflectionTestUtils.setField(service, "followService", followService);
        ReflectionTestUtils.setField(service, "shopRatingRefreshService", refreshService);

        Result result = service.saveBlog(blog);

        assertEquals(11L, result.getData());
        verify(refreshService).triggerFromBlog(3L);
    }

    @Test
    void should_skip_rating_refresh_when_blog_has_no_shop() {
        UserDTO user = new UserDTO();
        user.setId(8L);
        UserHolder.saveUser(user);

        ShopRatingRefreshService refreshService = Mockito.mock(ShopRatingRefreshService.class);
        IFollowService followService = Mockito.mock(IFollowService.class);
        QueryChainWrapper<Follow> query = Mockito.mock(QueryChainWrapper.class);
        when(followService.query()).thenReturn(query);
        when(query.eq("follow_user_id", 8L)).thenReturn(query);
        when(query.list()).thenReturn(Collections.emptyList());

        Blog blog = blogWithId(12L, null);
        BlogServiceImpl service = new BlogServiceImpl() {
            @Override
            public boolean save(Blog entity) {
                return true;
            }
        };
        ReflectionTestUtils.setField(service, "followService", followService);
        ReflectionTestUtils.setField(service, "shopRatingRefreshService", refreshService);

        service.saveBlog(blog);

        verify(refreshService, never()).triggerFromBlog(Mockito.anyLong());
    }

    private Blog blogWithId(Long id, Long shopId) {
        Blog blog = new Blog();
        blog.setId(id);
        blog.setShopId(shopId);
        return blog;
    }
}
