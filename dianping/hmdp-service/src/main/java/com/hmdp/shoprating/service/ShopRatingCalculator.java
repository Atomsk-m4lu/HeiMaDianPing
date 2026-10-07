package com.hmdp.shoprating.service;

import com.hmdp.entity.Blog;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
public class ShopRatingCalculator {

    public int calculateScore(List<Blog> blogs) {
        if (blogs == null || blogs.isEmpty()) {
            return 30;
        }
        double totalWeight = 0D;
        double weightedInteraction = 0D;
        for (Blog blog : blogs) {
            Instant createdAt = blog.getCreateTime() == null
                    ? Instant.now()
                    : blog.getCreateTime().atZone(ZoneId.systemDefault()).toInstant();
            double ageDays = Math.max(0D, Duration.between(createdAt, Instant.now()).toDays());
            double timeWeight = Math.max(0.2D, 1.0D - ageDays / 30D);
            int liked = blog.getLiked() == null ? 0 : blog.getLiked();
            int comments = blog.getComments() == null ? 0 : blog.getComments();
            double interactionBoost = Math.min(2.0D, liked / 80D + comments / 40D);
            double weight = timeWeight * (1.0D + interactionBoost);
            totalWeight += weight;
            weightedInteraction += interactionBoost * weight;
        }
        double normalizedBoost = totalWeight == 0D ? 0D : weightedInteraction / totalWeight;
        int score = (int) Math.round(30D + Math.min(20D, normalizedBoost * 10D));
        return Math.max(10, Math.min(50, score));
    }
}
