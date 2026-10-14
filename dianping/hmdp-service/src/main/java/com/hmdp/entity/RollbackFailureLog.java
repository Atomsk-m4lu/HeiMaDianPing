package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存回滚失败日志实体 —— 对账体系"失败路径留痕"的最后一环。
 *
 * <p>触发场景：Redis 回滚脚本执行失败（Redis 抖动、网络异常等），
 * 意味着预扣库存没有释放、用户名额被幽灵占用。此前只有一行 error 日志，
 * 现在落库为可审计、可告警、可工单化的记录，与 SeckillReconcileScanJob
 * 的双向对账（有 DEDUCT 无订单）形成互补：对账负责"发现"，本表负责"留痕与告警"。</p>
 *
 * <p>字段说明：source 标记触发链路（MAIN_CHAIN=主链路发送失败/CATCH 分支，
 * LISTENER=消费端 OUT_OF_STOCK 回滚失败）；alertStatus=PENDING 供告警系统扫描，
 * 人工处理后置 HANDLED。</p>
 */
@Data
@Accessors(chain = true)
@TableName("tb_rollback_failure_log")
public class RollbackFailureLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单 ID（秒杀预生成订单号） */
    private Long orderId;

    /** 用户 ID */
    private Long userId;

    /** 券 ID */
    private Long voucherId;

    /** Redis trace ID（与对账日志/追踪快照同一标识） */
    private String traceId;

    /** 触发来源：MAIN_CHAIN / LISTENER */
    private String source;

    /** 回滚结果码/异常摘要（如 REDIS_EXECUTE_ERROR） */
    private String resultCode;

    /** 回滚尝试次数（当前实现为单次尝试，字段为后续重试演进预留） */
    private Integer retryAttempts;

    /** 异常详情（截断至 1000 字符） */
    private String detail;

    /** 告警状态：PENDING=待处理，HANDLED=已处理 */
    private String alertStatus;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
