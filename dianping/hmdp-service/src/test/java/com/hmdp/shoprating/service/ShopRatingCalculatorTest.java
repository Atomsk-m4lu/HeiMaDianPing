package com.hmdp.shoprating.service;

import com.hmdp.entity.Blog;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopRatingCalculatorTest {

    @Test
    void should_raise_score_for_recent_and_interactive_blogs() {
        Blog recent = new Blog();
        recent.setLiked(80);
        recent.setComments(20);
        recent.setCreateTime(LocalDateTime.now().minusDays(1));

        Blog old = new Blog();
        old.setLiked(3);
        old.setComments(1);
        old.setCreateTime(LocalDateTime.now().minusDays(25));

        ShopRatingCalculator calculator = new ShopRatingCalculator();

        int score = calculator.calculateScore(Arrays.asList(recent, old));

        assertTrue(score >= 30);
        assertTrue(score <= 50);
    }

    @Test
    void should_fall_back_to_base_score_when_sample_is_empty() {
        ShopRatingCalculator calculator = new ShopRatingCalculator();
        assertEquals(30, calculator.calculateScore(Collections.emptyList()));
    }

    @Test
    void should_clamp_score_to_valid_range() {
        Blog hot = new Blog();
        hot.setLiked(10000);
        hot.setComments(5000);
        hot.setCreateTime(LocalDateTime.now());

        ShopRatingCalculator calculator = new ShopRatingCalculator();

        assertEquals(50, calculator.calculateScore(Collections.singletonList(hot)));
    }
}
