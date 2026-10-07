package com.hmdp.service;

/**
 * 秒杀订单创建结果枚举 — 异步建单流程的返回值，驱动 Listener 的分支处理。
 *
 * <p>各结果含义：
 * <ul>
 *   <li><b>CREATED</b> — 订单创建成功，库存扣减完成，后续写入对账追踪</li>
 *   <li><b>DUPLICATE</b> — 重复订单（主键冲突），幂等忽略，不做回滚</li>
 *   <li><b>OUT_OF_STOCK</b> — DB库存不足，事务回滚，需调用 rollbackSeckillVoucherPlus 回退 Redis 库存</li>
 *   <li><b>ROLLBACK_FAILED</b> — 回滚操作失败，留给对账引擎兜底处理</li>
 * </ul></p>
 */
public enum VoucherOrderCreateResult {
    CREATED,
    DUPLICATE,
    OUT_OF_STOCK,
    ROLLBACK_FAILED
}
