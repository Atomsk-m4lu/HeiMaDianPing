package com.hmdp.interceptor;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.metrics.MetricsRecorder;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

/**
 * 秒杀入口滑动窗口限流拦截器 —— 应用层第二道闸（第一道在网关/压测工具侧）。
 *
 * <p>注册在 {@link LoginInterceptor}（order=1）之后（order=2），
 * 保证拦截时 UserHolder 中已有登录用户；按 userId 维度用 Redis ZSET + Lua 做滑动窗口计数，
 * 超限直接返回 429，不进入秒杀主链路。</p>
 *
 * <p>为什么不用 Sentinel：当前是单机学习环境，自研轻量级实现便于把"窗口淘汰 + 计数 + 记录"
 * 的原子性讲清楚；限流算法与 Sentinel 的滑动窗口同构，是选型对照的现成弹药。</p>
 *
 * <p>全部参数可配（application.yaml）：
 * {@code seckill.rate-limit.sliding-window.enabled / window-ms / max-requests}。</p>
 */
@Slf4j
@Component
public class SlidingWindowInterceptor implements HandlerInterceptor {

    /** 滑动窗口限流 Lua 脚本（淘汰 → 计数 → 记录，单脚本原子完成） */
    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT;

    static {
        SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>();
        SLIDING_WINDOW_SCRIPT.setLocation(new ClassPathResource("lua/sliding_window.lua"));
        SLIDING_WINDOW_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 业务指标登记处（批次2 ③）：限流拒绝事件计数 */
    @Resource
    private MetricsRecorder metricsRecorder;

    /** 限流开关：压测对比实验用（开/关两组数据） */
    @Value("${seckill.rate-limit.sliding-window.enabled:true}")
    private boolean enabled;

    /** 窗口长度（毫秒），默认 1 秒 */
    @Value("${seckill.rate-limit.sliding-window.window-ms:1000}")
    private long windowMs;

    /** 窗口内最大请求数，默认 5 次 */
    @Value("${seckill.rate-limit.sliding-window.max-requests:5}")
    private int maxRequests;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enabled) {
            return true;
        }
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 未登录请求交给 LoginInterceptor 处理，这里不重复拦截
            return true;
        }
        String key = RedisConstants.SECKILL_RATE_LIMIT_KEY + user.getId();
        long now = System.currentTimeMillis();
        String member = now + "-" + UUID.randomUUID().toString().substring(0, 8);
        Long result = stringRedisTemplate.execute(
                SLIDING_WINDOW_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(windowMs),
                String.valueOf(maxRequests),
                member
        );
        if (result != null && result == 1L) {
            return true;
        }
        log.warn("sliding window rate limit hit, userId={}, uri={}", user.getId(), request.getRequestURI());
        metricsRecorder.incRatelimitRejected();
        writeTooManyRequests(response);
        return false;
    }

    /** 429 响应：与全局 Result 壳保持一致，前端/压测脚本按错误码区分限流与业务失败 */
    private void writeTooManyRequests(HttpServletResponse response) throws Exception {
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSONUtil.toJsonStr(Result.fail("请求过于频繁，请稍后再试")));
    }
}
