package com.hmdp.listener;

import com.hmdp.dto.SeckillVoucherOrderMessage;
import com.hmdp.seckill.application.service.SeckillOrderTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单超时监听器 — 消费 order-timeout 延时消息，关闭超时未支付订单。
 *
 * <p>在秒杀Plus流程中的位置：
 * <pre>{@code
 *   VoucherOrderServiceImpl.seckillVoucherPlus()
 *     → 发送 order-timeout 延时消息（delayLevel=15，实际 20 分钟）
 *     → 20分钟后 → 【本Listener】消费 → 触发关单扫描（兜底 Job 失效时的最后备份）
 * }</pre></p>
 *
 * <p>处理逻辑：
 * <ol>
 *   <li>校验消息有效性（非空，orderId非空）</li>
 *   <li>检查订单是否存在（已支付或已取消的订单不处理）</li>
 *   <li>调用 closeTimeoutOrders() 扫描并关闭超时未支付订单</li>
 * </ol></p>
 *
 * <p>正反关系：正常关单由 SeckillTimeoutCloseFallbackJob 每 2 分钟扫描完成（15 分钟门槛，约 16 分钟关单）；
 * 本Listener 的 20 分钟延时消息是最后一道备份——兜底 Job 若失效（漏启调度、DB 故障等），由它兜住。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "seckill-plus-order-topic",
        consumerGroup = "seckill-plus-timeout-consumer-group",
        selectorExpression = "order-timeout"
)
public class SeckillOrderTimeoutListener implements RocketMQListener<SeckillVoucherOrderMessage> {

    private final SeckillOrderTimeoutService seckillOrderTimeoutService;

    @Override
    public void onMessage(SeckillVoucherOrderMessage voucherOrder) {
        if (voucherOrder == null || voucherOrder.getOrderId() == null) {
            return;
        }
        log.info("received timeout message for orderId={}", voucherOrder.getOrderId());

        // 订单不存在说明已经支付/取消/退款了，无需处理
        if (seckillOrderTimeoutService == null || !seckillOrderTimeoutService.orderExists(voucherOrder.getOrderId())) {
            return;
        }

        // 触发超时关单逻辑：扫描 UNPAID 状态订单，超过15分钟的取消并回滚库存
        seckillOrderTimeoutService.closeTimeoutOrders();
    }
}
