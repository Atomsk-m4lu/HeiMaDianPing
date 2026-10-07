package com.hmdp.seckill.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.VoucherReconcileLog;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mapper.VoucherReconcileLogMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.shared.reconciliation.model.ReconcileBusinessType;
import com.hmdp.shared.reconciliation.model.ReconcileLogType;
import com.hmdp.shared.reconciliation.model.ReconciliationStatus;
import com.hmdp.shared.reconciliation.model.VoucherOrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeckillOrderTimeoutService {

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Resource
    private VoucherReconcileLogMapper voucherReconcileLogMapper;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    public void closeTimeoutOrders() {
        QueryWrapper<VoucherOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("status", VoucherOrderStatus.UNPAID).last("limit 20");
        List<VoucherOrder> orders = voucherOrderMapper.selectList(wrapper);
        for (VoucherOrder order : orders) {
            if (order.getCreateTime() == null || order.getCreateTime().isAfter(LocalDateTime.now().minusMinutes(15))) {
                continue;
            }
            order.setStatus(VoucherOrderStatus.CANCELLED);
            order.setUpdateTime(LocalDateTime.now());
            voucherOrderMapper.updateById(order);
            restoreMysqlStock(order);
            restoreRedisReservation(order);
        }
    }

    public boolean orderExists(Long orderId) {
        return voucherOrderMapper.selectById(orderId) != null;
    }

    private void restoreMysqlStock(VoucherOrder order) {
        seckillVoucherService.update()
                .setSql("stock = stock + 1")
                .eq("voucher_id", order.getVoucherId())
                .update();
    }

    private void restoreRedisReservation(VoucherOrder order) {
        if (stringRedisTemplate == null) {
            return;
        }
        String traceId = order.getTraceId() == null ? "trace-" + order.getId() : order.getTraceId();
        stringRedisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<Long>() {{
                    setLocation(new org.springframework.core.io.ClassPathResource("seckill_plus_rollback.lua"));
                    setResultType(Long.class);
                }},
                java.util.Collections.emptyList(),
                order.getVoucherId().toString(),
                order.getUserId().toString(),
                order.getId().toString(),
                traceId,
                String.valueOf(2 * 24 * 60 * 60L),
                String.valueOf(System.currentTimeMillis())
        );
        voucherReconcileLogMapper.insert(new VoucherReconcileLog()
                .setOrderId(order.getId())
                .setUserId(order.getUserId())
                .setVoucherId(order.getVoucherId())
                .setTraceId(traceId)
                .setChangeQty(1)
                .setLogType(ReconcileLogType.RESTORE)
                .setBusinessType(ReconcileBusinessType.TIMEOUT)
                .setReconciliationStatus(ReconciliationStatus.PENDING)
                .setDetail("timeout close fallback"));
    }
}
