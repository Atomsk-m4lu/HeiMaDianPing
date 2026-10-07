package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hmdp.shared.reconciliation.model.ReconcileBusinessType;
import com.hmdp.shared.reconciliation.model.ReconcileLogType;
import com.hmdp.shared.reconciliation.model.ReconciliationStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券对账日志实体 — 记录每次库存变更（扣减/回补）的完整快照。
 *
 * <p>作用：为对账引擎提供 Redis 与 DB 之间的库存变更审计依据。
 * 每次在 Redis Lua 脚本中扣减/回补库存，或 DB 层创建订单/回滚订单时，
 * 都会写入一条对账日志，记录操作前后的库存数量。</p>
 *
 * <p>典型流程：
 * <pre>{@code
 *   Lua扣减 → DEDUCT日志（Redis侧，beforeQty=5, afterQty=4）
 *   DB建单  → DEDUCT日志（DB侧，beforeQty=5, afterQty=4）
 *   对账引擎 → 比对两边的DEDUCT日志是否一致
 *   回滚    → RESTORE日志（库存+1）
 * }</pre></p>
 */
@Data
@Accessors(chain = true)
@TableName("tb_voucher_reconcile_log")
public class VoucherReconcileLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联的订单ID */
    private Long orderId;

    /** 下单用户ID */
    private Long userId;

    /** 秒杀优惠券ID */
    private Long voucherId;

    /** 关联的MQ消息ID（用于可靠消息追溯） */
    private String messageId;

    /** 操作详情描述（如 "db order created and stock deducted"） */
    private String detail;

    /** 本次操作前的库存数量 */
    private Integer beforeQty;

    /** 本次变更的库存数量（扣减为1，回补为1） */
    private Integer changeQty;

    /** 本次操作后的库存数量（beforeQty - changeQty 或 beforeQty + changeQty） */
    private Integer afterQty;

    /** Redis trace ID，串联 Lua 预扣与后续 DEDUCT/RESTORE 日志 */
    private String traceId;

    /** 日志类型：DEDUCT=库存扣减，RESTORE=库存回补 */
    private ReconcileLogType logType;

    /** 业务类型：SUCCESS=正常流程，FAIL=失败回滚，TIMEOUT=超时取消 */
    private ReconcileBusinessType businessType;

    /** 对账状态：PENDING=待对账，后续由对账引擎流转为 COMPLETED 等 */
    private ReconciliationStatus reconciliationStatus;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
