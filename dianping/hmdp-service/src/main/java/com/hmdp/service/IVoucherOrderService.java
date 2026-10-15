package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillVoucherOrderMessage;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 优惠券订单服务接口
 * <p>
 * 继承 MyBatis-Plus 的 {@link IService}，提供优惠券订单相关的业务操作。
 * 包含两套秒杀方案：
 * <ul>
 *   <li><b>原始秒杀</b>：Redis Lua 扣库存 + 同步建单 + Redisson 分布式锁，适合中等并发场景</li>
 *   <li><b>秒杀Plus</b>：Redis Lua 原子校验扣减 + RocketMQ 异步建单 + 对账追踪，适合高并发场景</li>
 * </ul>
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 原始秒杀下单（同步建单版本）
     * <p>
     * 执行 Redis Lua 脚本原子校验一人一单 + 扣减库存，校验通过后同步调用
     * {@link #createVoucherOrder} 完成数据库订单插入和库存扣减。
     * </p>
     *
     * @param voucherId 秒杀优惠券 ID
     * @return 秒杀结果，成功时返回 orderId
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 秒杀Plus下单（异步建单版本，推荐）
     * <p>
     * 完整链路：RedisIdWorker 生成 orderId → Redis Lua 原子校验（请求去重 + 库存 + 一人一单）
     * → 可靠 MQ 发送建单消息 → 发送延时关单消息 → 返回 orderId。
     * MQ 发送失败时自动回滚 Redis 库存。
     * </p>
     *
     * @param voucherId 秒杀优惠券 ID
     * @return 秒杀结果，成功时返回 orderId
     */
    Result seckillVoucherPlus(Long voucherId);

    /**
     * 原始秒杀的同步建单方法（已废弃）
     * <p>
     * 在 {@code @Transactional} 事务中完成：一人一单校验 → DB 乐观锁扣减库存 → 订单入库。
     * 由 Redisson 分布式锁（见 {@link #handleVoucherOrder}）保证同一用户不会并发建单。
     * </p>
     *
     * @param voucherOrder 待创建的优惠券订单
     */
    void createVoucherOrder(VoucherOrder voucherOrder);

    /**
     * 秒杀Plus的异步建单方法（由 MQ 消费者调用）
     * <p>
     * 在 {@code @Transactional} 事务中完成三项操作：
     * <ol>
     *   <li>插入订单（主键冲突 → {@link VoucherOrderCreateResult#DUPLICATE}，幂等处理）</li>
     *   <li>DB 乐观锁扣减库存（库存不足 → 事务回滚，返回 {@link VoucherOrderCreateResult#OUT_OF_STOCK}）</li>
     *   <li>写入 DEDUCT 类型对账日志</li>
     * </ol>
     * </p>
     *
     * @param message MQ 消息体，包含 orderId、userId、voucherId、traceId
     * @return 创建结果：CREATED（成功）、DUPLICATE（幂等忽略）、OUT_OF_STOCK（DB 库存不足）
     */
    VoucherOrderCreateResult createVoucherOrderPlus(SeckillVoucherOrderMessage message);

    /**
     * 回滚秒杀Plus订单，恢复 Redis 中已扣减的库存
     * <p>
     * 触发场景：MQ 发送失败、DB 库存不足、或其他需要回退 Redis 预扣库存的异常。
     * 通过 seckill_plus_rollback.lua 原子执行恢复，脚本内置幂等保护。
     * </p>
     *
     * @param voucherOrder 需要回滚的订单
     */
    void rollbackSeckillVoucherPlus(VoucherOrder voucherOrder);

    /**
     * 判断指定订单是否已存在于数据库
     * <p>
     * 用于超时关单前的校验、建单校验任务的轮询检查等场景。
     * </p>
     *
     * @param orderId 订单 ID
     * @return {@code true} 存在，{@code false} 不存在
     */
    boolean existsVoucherOrder(Long orderId);

    /**
     * Redisson 分布式锁保护下的订单创建（原始秒杀使用）
     * <p>
     * 以 {@code lock:order:{userId}} 为锁键，保证同一用户不会并发创建订单。
     * 锁内通过 Spring AOP 代理调用 {@link #createVoucherOrder} 以确保事务生效。
     * </p>
     *
     * @param voucherOrder 待创建的优惠券订单
     */
    void handleVoucherOrder(VoucherOrder voucherOrder);
}