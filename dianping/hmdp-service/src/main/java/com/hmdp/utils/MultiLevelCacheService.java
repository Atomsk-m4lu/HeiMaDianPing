package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 多级缓存服务（批次3 ②）—— L1 本地（Caffeine）叠加在 L2 Redis 逻辑过期之上。
 *
 * <p>设计要点（与参考实现的差异即"修坑点"）：
 * <ol>
 *   <li><b>L1 存完整的 RedisData 结构</b>（含逻辑过期时间）——命中也要校验过期，
 *       避免"本地缓存把过期数据续命"放大脏读窗口；</li>
 *   <li><b>L2 直接委派既有的 {@link CacheClient#queryWithLogicalExpire}</b>
 *       （互斥锁 + 独立线程池异步重建），模板零改动——多级是"叠加"不是"替换"；</li>
 *   <li><b>失效广播用 RocketMQ BROADCASTING 模式</b>（每实例必达，见 CacheInvalidationListener），
 *       广播失败只降级为日志——L1 条目自身的逻辑过期时间是最终兜底。</li>
 * </ol></p>
 *
 * <p>读路径三条分支：L1 命中未过期 → 直接返回；L1 未命中或已逻辑过期 → 读 L2 原始结构并回填 L1，
 * L2 未过期返回，已过期则委派模板触发重建并返回旧数据（与逻辑过期语义一致）。</p>
 */
@Slf4j
@Component
public class MultiLevelCacheService {

    /** 缓存失效广播 topic（BROADCASTING 模式消费；只在 producer 侧拼 destination 用） */
    public static final String CACHE_INVALIDATION_TOPIC = "hmdp-cache-invalidation-topic";

    /** 缓存失效广播 tag */
    public static final String CACHE_INVALIDATION_TAG = "shop";

    /** L1 本地缓存（由 LocalCacheConfig 构建，含命中率统计） */
    @Resource
    private Cache<String, Object> shopLocalCache;

    /** L2：既有单级缓存模板（逻辑过期 + 互斥锁 + 异步重建），零改动复用 */
    @Resource
    private CacheClient cacheClient;

    /** 用于读取 Redis 原始结构（仅为拿到真实的逻辑过期时间；重建逻辑仍归 CacheClient） */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 失效广播发送器（best-effort，失败只记日志） */
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 多级读取：L1 本地 → L2 Redis（逻辑过期）→ DB（模板内）。
     *
     * @param keyPrefix  Redis key 前缀（如 cache:shop:）
     * @param id         业务 ID
     * @param type       返回类型
     * @param dbFallback 查库函数（仅重建时被模板调用）
     * @param time       逻辑过期时长
     * @param unit       时间单位
     */
    @SuppressWarnings("unchecked")
    public <R, ID> R queryWithLocalCache(String keyPrefix, ID id, Class<R> type,
                                         Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        // ── L1：命中且未逻辑过期 → 直接返回 ──
        Object cached = shopLocalCache.getIfPresent(key);
        if (cached instanceof RedisData && isFresh((RedisData) cached)) {
            log.debug("[多级缓存] L1 命中: {}", key);
            return (R) ((RedisData) cached).getData();
        }

        // ── L1 未命中或已逻辑过期 → 读 L2 原始结构（空白缓存的语义与单级模板一致：依赖预热）──
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R data = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 回填 L1：data 存"已反序列化的对象"（而非原始 JSONObject，避免命中时类型转换异常），
        // 过期时间沿用 Redis 侧的真实值——过期条目不会被"续命"，下次命中自然回落到 L2
        RedisData l1Data = new RedisData();
        l1Data.setData(data);
        l1Data.setExpireTime(redisData.getExpireTime());
        shopLocalCache.put(key, l1Data);
        if (isFresh(redisData)) {
            log.debug("[多级缓存] L2 命中: {}", key);
            return data;
        }

        // ── L2 逻辑过期 → 委派既有模板（互斥锁 + 异步重建），返回旧数据保证可用性 ──
        R rebuilt = cacheClient.queryWithLogicalExpire(keyPrefix, id, type, dbFallback, time, unit);
        return rebuilt != null ? rebuilt : data;
    }

    /**
     * 双层驱逐（仅本实例）：L1 本地 + L2 Redis。幂等，可重复调用。
     */
    public void evict(String keyPrefix, Object id) {
        String key = keyPrefix + id;
        shopLocalCache.invalidate(key);
        stringRedisTemplate.delete(key);
    }

    /**
     * 双层驱逐 + 广播其他实例清理各自 L1（写路径："先更库 → 双删 → 广播"的最后一步）。
     *
     * <p>广播是 best-effort：发送失败只记日志——其他实例的 L1 最多脏读到
     * 自身逻辑过期时间为止（L1 条目自带过期校验，兜底在本地）。</p>
     */
    public void evictAndBroadcast(String keyPrefix, Object id) {
        evict(keyPrefix, id);
        try {
            rocketMQTemplate.syncSend(CACHE_INVALIDATION_TOPIC + ":" + CACHE_INVALIDATION_TAG, String.valueOf(id));
        } catch (Exception e) {
            log.warn("[多级缓存] 广播失效发送失败（L1 逻辑过期兜底）, key={}", keyPrefix + id, e);
        }
    }

    /** 逻辑过期校验：带过期时间且尚未到期 */
    private boolean isFresh(RedisData redisData) {
        return redisData.getExpireTime() != null && redisData.getExpireTime().isAfter(LocalDateTime.now());
    }
}
