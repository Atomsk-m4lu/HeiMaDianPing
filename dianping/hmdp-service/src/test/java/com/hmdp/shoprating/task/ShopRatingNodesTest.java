package com.hmdp.shoprating.task;

import com.hmdp.entity.Blog;
import com.hmdp.shoprating.domain.model.ShopRatingRefreshMessage;
import com.hmdp.shoprating.domain.model.ShopRatingRefreshTaskContext;
import com.hmdp.shoprating.domain.model.ShopRatingTriggerSource;
import com.hmdp.shoprating.service.ShopRatingCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopRatingNodesTest {

    @Test
    void risk_filter_should_terminate_when_no_valid_blogs_exist() {
        ShopRatingRefreshTaskContext context = new ShopRatingRefreshTaskContext();
        context.setInput(new ShopRatingRefreshMessage(1L, ShopRatingTriggerSource.BLOG_PUBLISHED, System.currentTimeMillis()));
        context.setAttribute("rawBlogs", Collections.emptyList());

        RiskFilterNode node = new RiskFilterNode();
        node.execute(context);

        assertTrue(context.isTerminateChain());
    }

    @Test
    void calc_node_should_store_calculated_score() {
        Blog blog = new Blog();
        blog.setLiked(10);
        blog.setComments(2);
        blog.setCreateTime(LocalDateTime.now().minusDays(1));

        ShopRatingRefreshTaskContext context = new ShopRatingRefreshTaskContext();
        context.setInput(new ShopRatingRefreshMessage(1L, ShopRatingTriggerSource.BLOG_PUBLISHED, System.currentTimeMillis()));
        context.setAttribute("validBlogs", Arrays.asList(blog));

        CalcWeightedScoreNode node = new CalcWeightedScoreNode(new ShopRatingCalculator());
        node.execute(context);

        assertNotNull(context.getAttribute("calculatedScore"));
    }
}
