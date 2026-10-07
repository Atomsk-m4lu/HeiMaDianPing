package com.hmdp.shopdetail.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 达人探店博客值对象
 * <p>
 * 记录用户发布的探店图文内容，包含标题、图片、正文、点赞数等。
 * 用于详情页"达人探店"区域，辅助用户做到店决策。
 * </p>
 */
@Data
public class ShopVisitBlog {

    private Long id;
    private Long shopId;
    private Long userId;
    private String userName;
    private String userIcon;
    private Boolean likedByCurrentUser;
    private String title;
    private String images;
    private String content;
    private Integer likedCount;
    private Integer commentCount;
    private LocalDateTime createTime;
}
