package com.hmdp.job.seckill;

import com.hmdp.seckill.application.service.SeckillOrderTimeoutService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 秒杀超时关单兜底Job — 每2分钟扫描一次，关闭超时未支付的订单。
 *
 * <p>双保险机制中的兜底路径：
 * <ul>
 *   <li>主路径：SeckillOrderTimeoutListener（MQ延时14分钟消息驱动）</li>
 *   <li>兜底路径：本Job每2分钟扫描 UNPAID 订单，关闭超过15分钟未支付的</li>
 * </ul></p>
 *
 * <p>兜底的意义：MQ延时消息可能丢失或延迟不准，定时Job作为最后的保障，
 * 确保超时未支付的库存最终被释放。</p>
 */
@Component
@Slf4j
public class SeckillTimeoutCloseFallbackJob {

    @Resource
    private SeckillOrderTimeoutService seckillOrderTimeoutService;

    /** 每2分钟扫描一次 */
    @Scheduled(cron = "0 0/2 * * * ?")
    public void run() {
        if (seckillOrderTimeoutService == null) {
            return;
        }
        try {
            seckillOrderTimeoutService.closeTimeoutOrders();
        } catch (Exception e) {
            log.warn("timeout fallback close failed", e);
        }
    }
}
