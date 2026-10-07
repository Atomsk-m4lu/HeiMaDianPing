package com.hmdp.shopdetail.domain;

import com.hmdp.shopdetail.domain.model.ShopDetailAggregate;
import com.hmdp.shopdetail.domain.model.ShopMarketingOffer;
import com.hmdp.shopdetail.domain.model.ShopReview;
import com.hmdp.shopdetail.domain.model.ShopReviewSummary;
import com.hmdp.shopdetail.domain.model.ShopVisitBlog;
import com.hmdp.shopdetail.domain.repository.GuaranteeClaimRepository;
import com.hmdp.shopdetail.domain.repository.ShopDetailRepository;
import com.hmdp.shopdetail.domain.repository.ShopReviewRepository;
import com.hmdp.shopdetail.domain.service.ShopDetailDomainService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopDetailDomainServiceTest {

    @Test
    void should_enrich_shop_detail_with_recommendations_extra_info_and_related_shops() {
        ShopDetailAggregate aggregate = new ShopDetailAggregate();
        aggregate.setShopId(1L);
        aggregate.setShopName("103茶餐厅");
        aggregate.setTypeId(2L);
        aggregate.setAddress("浦东新区张杨路 88 号");
        aggregate.setArea("陆家嘴");
        aggregate.setDistance(520D);
        aggregate.setAvgPrice(98L);
        aggregate.setScore(48);
        aggregate.setOpenHours("10:00-22:00");
        aggregate.setImages("cover.jpg,room.jpg");
        aggregate.setVouchers(Arrays.asList(
                offer(1001L, "双人套餐", "招牌双人餐", 168L, 228L),
                offer(1002L, "代金券", "100 抵 120", 100L, 120L)
        ));
        aggregate.setBlogs(Collections.singletonList(blog("环境很适合约会")));

        ShopReviewSummary summary = new ShopReviewSummary();
        summary.setTotalReviews(30);
        summary.setAverageScore(46);
        summary.setRecommendationRate(90);
        summary.setTopTags(Arrays.asList("环境舒服", "出餐快", "适合约会"));

        ShopReviewRepository reviewRepository = new ShopReviewRepository() {
            @Override
            public java.util.List<ShopReview> findByShopId(Long shopId, int limit) {
                return Collections.singletonList(review("服务不错", "环境舒服", "适合约会"));
            }

            @Override
            public ShopReviewSummary summarize(Long shopId) {
                return summary;
            }
        };

        ShopDetailRepository detailRepository = new ShopDetailRepository() {
            @Override
            public ShopDetailAggregate findBaseDetail(Long shopId) {
                return aggregate;
            }

            @Override
            public java.util.List<ShopDetailAggregate> findNearbyShops(Long typeId, Double x, Double y, int current) {
                ShopDetailAggregate nearby = new ShopDetailAggregate();
                nearby.setShopId(2L);
                nearby.setShopName("港式小馆");
                nearby.setArea("陆家嘴");
                nearby.setAvgPrice(88L);
                nearby.setScore(45);
                nearby.setDistance(860D);
                return Collections.singletonList(nearby);
            }

            @Override
            public java.util.List<ShopMarketingOffer> findOffers(Long shopId) {
                return aggregate.getVouchers();
            }

            @Override
            public java.util.List<ShopVisitBlog> findVisitBlogs(Long shopId, int limit) {
                return aggregate.getBlogs();
            }
        };

        ShopDetailDomainService service = new ShopDetailDomainService(detailRepository, reviewRepository, new NoopGuaranteeClaimRepository());

        ShopDetailAggregate result = service.getShopDetail(1L);

        assertNotNull(result.getExtraInfo());
        assertTrue(result.getExtraInfo().getTrafficTips().contains("地铁"));
        assertFalse(result.getRecommendations().isEmpty());
        assertEquals("signatureDish", result.getRecommendations().get(0).getType());
        assertEquals("双人套餐", result.getRecommendations().get(0).getTitle());
        assertFalse(result.getRecommendations().get(0).getItems().isEmpty());
        assertEquals("recommendReason", result.getRecommendations().get(1).getType());
        assertTrue(result.getRecommendations().get(1).getTags().contains("高推荐率"));
        assertEquals("rankingCard", result.getRecommendations().get(2).getType());
        assertEquals("口碑榜", result.getRecommendations().get(2).getBadge());
        assertFalse(result.getRelatedShops().isEmpty());
        assertEquals("港式小馆", result.getRelatedShops().get(0).getShopName());
        assertTrue(result.getTags().stream().anyMatch(tag -> "review".equals(tag.getSource())));
    }

    private ShopMarketingOffer offer(Long id, String title, String subTitle, Long payValue, Long actualValue) {
        ShopMarketingOffer offer = new ShopMarketingOffer();
        offer.setId(id);
        offer.setTitle(title);
        offer.setSubTitle(subTitle);
        offer.setPayValue(payValue);
        offer.setActualValue(actualValue);
        return offer;
    }

    private ShopVisitBlog blog(String content) {
        ShopVisitBlog blog = new ShopVisitBlog();
        blog.setContent(content);
        blog.setTitle("达人探店");
        return blog;
    }

    private ShopReview review(String content, String... tags) {
        ShopReview review = new ShopReview();
        review.setContent(content);
        review.setOverallScore(46);
        review.setTags(Arrays.asList(tags));
        return review;
    }

    private static class NoopGuaranteeClaimRepository implements GuaranteeClaimRepository {

        @Override
        public com.hmdp.shopdetail.domain.model.GuaranteeClaim save(com.hmdp.shopdetail.domain.model.GuaranteeClaim claim) {
            return claim;
        }

        @Override
        public java.util.Optional<com.hmdp.shopdetail.domain.model.GuaranteeClaim> findByClaimNo(String claimNo) {
            return java.util.Optional.empty();
        }
    }
}
