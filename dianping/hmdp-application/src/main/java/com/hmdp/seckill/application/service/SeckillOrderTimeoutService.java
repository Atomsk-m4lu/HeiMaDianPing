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

    /**
     * 定时关单兜底 —— 秒杀订单创建后 15 分钟未支付则自动取消并恢复库存。
     *
     * <p>和正常关单链路的关系：
     * 正常关单靠 {@code VoucherOrderServiceImpl.publishDelay()} 发的延时 MQ 消息（10 分钟后触发），
     * 这里的定时 Job 是<b>第二道保险</b> —— 延时消息发失败（persistOnFailure 兜底）、
     * 或消费者挂了没消费，最终都会落到这里被捞起来关掉。</p>
     *
     * <p>为什么选 15 分钟而不是延时消息的 10 分钟？留 5 分钟 gap 给延时链路跑完，避免两边同时操作同一笔订单。</p>
     */
    public void closeTimeoutOrders() {
        // DB 查询只过滤 UNPAID + 按创建时间升序 + limit 20 —— 不在 SQL 里写死 15 分钟条件，
        // 而是拉出来后 Java 层二次过滤（见下面的 isAfter 判断）。
        // 原因：SQL 里写 "create_time <= now - 15min" 会导致这条时间条件无法被索引完全利用（索引在 status 上），
        // 拉全量 UNPAID 再按 create_time 排序，配合小批量 limit 反而更可控
        QueryWrapper<VoucherOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("status", VoucherOrderStatus.UNPAID).orderByAsc("create_time").last("limit 20");
        List<VoucherOrder> orders = voucherOrderMapper.selectList(wrapper);
        for (VoucherOrder order : orders) {
            // 二次过滤：只关"创建时间早于 15 分钟前"的 —— 就是上面说的 SQL 条件下沉到 Java
            // createTime null 是极端脏数据防御，直接跳过避免 NPE
            if (order.getCreateTime() == null || order.getCreateTime().isAfter(LocalDateTime.now().minusMinutes(15))) {
                continue;
            }
            // CAS 抢占式关单：eq("id", ...).eq("status", UNPAID) 双条件 ——
            // 如果 updated==0，说明状态已经变了（可能是延时消息已经关了，或用户自己支付了），
            // 无锁竞争抢占，避免重复关单导致库存回滚两次
            int updated = voucherOrderMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<VoucherOrder>()
                            .eq("id", order.getId())
                            .eq("status", VoucherOrderStatus.UNPAID)
                            .set("status", VoucherOrderStatus.CANCELLED)
                            .set("update_time", LocalDateTime.now()));
            if (updated == 0) {
                continue;
            }
            // 先恢复 MySQL 库存（持久层，DB 原子自增 stock=stock+1，防超卖）
            restoreMysqlStock(order);
            // 再恢复 Redis 库存（用 seckill_plus_rollback.lua 原子回滚 + 写 RESTORE 对账日志）
            // 顺序不能反：MySQL 是最终一致的兜底，先写 DB 再写 Redis，
            // 万一 Redis 回滚失败，对账日志缺了但 DB 库存已经恢复了，不会导致 Redis 超卖
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