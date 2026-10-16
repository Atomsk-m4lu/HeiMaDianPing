package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.SeckillReconcileTask;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.VoucherReconcileLog;
import com.hmdp.mapper.SeckillReconcileTaskMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mapper.VoucherReconcileLogMapper;
import com.hmdp.metrics.MetricsRecorder;
import com.hmdp.shared.reconciliation.model.ReconcileLogType;
import com.hmdp.shared.reconciliation.model.SeckillReconcileAnomalyType;
import com.hmdp.shared.reconciliation.model.SeckillReconcileCompensationStatus;
import com.hmdp.shared.reconciliation.model.SeckillReconcileTaskStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeckillReconcileScanService {

    private final SeckillReconcileTaskMapper taskMapper;
    private final VoucherOrderMapper voucherOrderMapper;
    private final VoucherReconcileLogMapper voucherReconcileLogMapper;
    private final MetricsRecorder metricsRecorder;
    private final int scanBatchSize;
    private final int scanDelayMinutes;
    private final int compensationMaxRetryCount;
    private final boolean autoCompensationEnabled;

    @Autowired
    public SeckillReconcileScanService(SeckillReconcileTaskMapper taskMapper,
                                       VoucherOrderMapper voucherOrderMapper,
                                       VoucherReconcileLogMapper voucherReconcileLogMapper,
                                       MetricsRecorder metricsRecorder,
                                       @Value("${seckill.reconcile.scan-batch-size:100}") int scanBatchSize,
                                       @Value("${seckill.reconcile.scan-delay-minutes:3}") int scanDelayMinutes,
                                       @Value("${seckill.reconcile.compensation-max-retry-count:5}") int compensationMaxRetryCount,
                                       @Value("${seckill.reconcile.auto-compensation-enabled:false}") boolean autoCompensationEnabled) {
        this.taskMapper = taskMapper;
        this.voucherOrderMapper = voucherOrderMapper;
        this.voucherReconcileLogMapper = voucherReconcileLogMapper;
        this.metricsRecorder = metricsRecorder;
        this.scanBatchSize = scanBatchSize;
        this.scanDelayMinutes = scanDelayMinutes;
        this.compensationMaxRetryCount = compensationMaxRetryCount;
        this.autoCompensationEnabled = autoCompensationEnabled;
    }

    /**
     * 对账扫描入口 —— 分别从"订单表"和"DEDUCT 对账日志表"两个视角交叉比对，
     * 找出两边不一致的记录并生成 {@link SeckillReconcileTask} 待处理。
     *
     * <p>扫描方向是不对称的：
     * <ul>
     *   <li>{@link #scanOrders()} —— 以订单表为主，查订单存在但 DEDUCT 日志缺失（"订单在但库存没扣"）</li>
     *   <li>{@link #scanDeductLogs()} —— 以 DEDUCT 日志为主，查日志存在但订单缺失（"库存扣了但订单没建"）</li>
     * </ul></p>
     *
     * <p>为什么不对称？因为两个方向的严重程度不同：
     * <ul>
     *   <li>订单在但 DEDUCT 日志没写 —— 很可能是消费者事务中途漏写，DB 库存实际也没扣，只是多了一笔空订单，
     *       业务侧可以让用户重新下单，自动补偿风险低</li>
     *   <li>DEDUCT 日志在但订单没建 —— 库存已经真的扣了，但订单数据丢失，用户付不了款也退不回库存，
     *       必须让 {@code autoCompensationEnabled} 开关控制是否自动补单，默认关（保守策略）</li>
     * </ul></p>
     *
     * <p>两条扫描都带 {@code scanDelayMinutes} 延迟窗口（默认 3 分钟），
     * 给异步链路留足时间（MQ 消费 → DB 事务 → 日志落库）再对账，避免把"正在跑"的短暂不一致误判成异常。</p>
     */
    public void scanRecentRecords() {
        scanOrders();
        scanDeductLogs();
    }

    private void scanOrders() {
        QueryWrapper<VoucherOrder> wrapper = new QueryWrapper<>();
        // 取"scanDelayMinutes 之前"的订单 —— 用 <= 而不是 >=，
        // 因为我们要找的是"等异步链路跑完还没对齐"的记录，最近几秒的订单 MQ 消费者可能还在处理中
        wrapper.le("create_time", LocalDateTime.now().minusMinutes(scanDelayMinutes))
                .last("limit " + scanBatchSize);
        List<VoucherOrder> orders = voucherOrderMapper.selectList(wrapper);
        for (VoucherOrder order : orders) {
            // N+1 查询：每条订单单独查一次 DEDUCT 日志。对账是离线低频任务（Job 每 5 分钟跑一次，
            // 每次最多 scanBatchSize=100 条），DB 查询量可控，没必要为了 JOIN/IN 把代码复杂化
            VoucherReconcileLog deductLog = voucherReconcileLogMapper.selectOne(new QueryWrapper<VoucherReconcileLog>()
                    .eq("order_id", order.getId())
                    .eq("log_type", ReconcileLogType.DEDUCT)
                    .last("limit 1"));
            if (deductLog == null) {
                // 异常：订单在但 DEDUCT 日志缺失 → 说明消费者可能 insert 成功但写日志时炸了，
                // DB 库存大概率也没扣。这里只建 task 不直接补偿 —— createTaskIfAbsent 内部会判断
                // autoCompensationEnabled 开关，决定是 pending 等人工还是 pendingCompensation 等自动
                createTaskIfAbsent(SeckillReconcileAnomalyType.ORDER_PRESENT_DEDUCT_MISSING,
                        order.getId(),
                        order.getUserId(),
                        order.getVoucherId(),
                        order.getTraceId(),
                        "order exists but deduct log missing");
            }
        }
    }

    private void scanDeductLogs() {
        QueryWrapper<VoucherReconcileLog> wrapper = new QueryWrapper<>();
        // 以 DEDUCT 日志为主视角 —— 前面 scanOrders 是反方向（订单表为主），
        // 两个方法合起来才是完整的双向对账：A 在 B 不在、B 在 A 不在都能捕获
        wrapper.eq("log_type", ReconcileLogType.DEDUCT)
                .le("create_time", LocalDateTime.now().minusMinutes(scanDelayMinutes))
                .last("limit " + scanBatchSize);
        List<VoucherReconcileLog> logs = voucherReconcileLogMapper.selectList(wrapper);
        for (VoucherReconcileLog log : logs) {
            // 这里比 scanOrders 的反向查询更简单 —— 直接 selectById，主键索引 O(1)
            VoucherOrder order = voucherOrderMapper.selectById(log.getOrderId());
            if (order == null) {
                // ⚠️ 严重异常：DEDUCT 日志存在意味着 DB 库存已经真扣了，但订单丢了！
                // 用户付不了款、退不回库存，属于数据层面的"库存黑洞"。
                // createTaskIfAbsent 内部对这个 anomalyType 有特殊处理：
                // autoCompensationEnabled=true 时走 pendingCompensation（自动补单），
                // 否则走 pending（等人工） —— 默认关，保守策略避免自动补偿再出错
                createTaskIfAbsent(SeckillReconcileAnomalyType.DEDUCT_PRESENT_ORDER_MISSING,
                        log.getOrderId(),
                        log.getUserId(),
                        log.getVoucherId(),
                        log.getTraceId(),
                        "deduct log exists but order missing");
            }
        }
    }

    private void createTaskIfAbsent(SeckillReconcileAnomalyType anomalyType,
                                    Long orderId,
                                    Long userId,
                                    Long voucherId,
                                    String traceId,
                                    String detail) {
        // 幂等检查：先查再插 —— 对账 Job 每轮 scanOrders + scanDeductLogs 可能同时发现同一条异常，
        // 用业务维度（anomalyType + orderId/traceId）去重，避免同一笔异常生成多条 task。
        // 这里没用数据库唯一键的原因：orderId 和 traceId 组合不一定唯一（DEDUCT_PRESENT_ORDER_MISSING
        // 这种异常 orderId 本身就是 null，只能靠 traceId 定位），唯一键没法覆盖所有情况
        if (countActiveTask(anomalyType, orderId, traceId) > 0) {
            return;
        }
        // 补偿策略分级：
        // 只有 autoCompensationEnabled=true 且异常类型是 DEDUCT_PRESENT_ORDER_MISSING（DEDUCT 日志在但订单丢了）
        // 才走 pendingCompensation 路线 —— 这种异常最危险（库存真扣了但订单没了），补偿收益最高，
        // 但也因为最危险，才必须让开关严格控制，避免误补偿；
        // 其他所有情况（包括 ORDER_PRESENT_DEDUCT_MISSING）一律走 pending 等人工，保守策略
        SeckillReconcileTask task = autoCompensationEnabled
                && anomalyType == SeckillReconcileAnomalyType.DEDUCT_PRESENT_ORDER_MISSING
                ? SeckillReconcileTask.pendingCompensation(anomalyType, orderId, userId, voucherId, traceId, detail,
                compensationMaxRetryCount)
                : SeckillReconcileTask.pending(anomalyType, orderId, userId, voucherId, traceId, detail,
                compensationMaxRetryCount);
        taskMapper.insert(task);
        metricsRecorder.incReconcileAnomaly();
    }

    /**
     * 判断当前异常是否已经存在未处理的恢复任务 —— createTaskIfAbsent 的幂等检查支撑。
     *
     * <p>"active" 定义：排除 RESOLVED（已解决）和 IGNORED（已忽略）这两个终态，
     * 剩下的 PENDING / PENDING_COMPENSATION / PROCESSING / RETRYING 都算"活跃中"，
     * 意味着这条异常已经被系统接管了，不需要再建一条 task。</p>
     *
     * <p>定位维度的动态选择：
     * <ul>
     *   <li>orderId != null → 按 orderId 查（ORDER_PRESENT_DEDUCT_MISSING：订单在，orderId 有值）</li>
     *   <li>orderId == null → fallback 按 traceId 查（DEDUCT_PRESENT_ORDER_MISSING：订单丢了，orderId 本身就是 null，
     *       只能靠 traceId 这个贯穿全链路的标识来定位）</li>
     * </ul></p>
     */
    private long countActiveTask(SeckillReconcileAnomalyType anomalyType, Long orderId, String traceId) {
        QueryWrapper<SeckillReconcileTask> wrapper = new QueryWrapper<>();
        // anomalyType + "非终态"是固定条件 —— 同一类型的异常不管什么时候发现，只要还没闭环就视为活跃
        wrapper.eq("anomaly_type", anomalyType.name())
                .notIn("task_status", SeckillReconcileTaskStatus.RESOLVED.name(), SeckillReconcileTaskStatus.IGNORED.name());
        // 定位维度二选一：有 orderId 用 orderId，没有用 traceId（对应 createTaskIfAbsent 里两种异常形态的差异）
        if (orderId != null) {
            wrapper.eq("order_id", orderId);
        } else {
            wrapper.eq("trace_id", traceId);
        }
        // selectCount 返回 Number（MyBatis-Plus 泛型），null-safe 处理避免 NPE
        Number count = taskMapper.selectCount(wrapper);
        return count == null ? 0L : count.longValue();
    }
}