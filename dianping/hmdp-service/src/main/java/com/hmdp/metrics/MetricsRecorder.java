package com.hmdp.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 业务指标登记处（批次2 ③）—— 把"可靠性事件"变成 Prometheus 可查询的数字。
 *
 * <p>设计要点：
 * <ol>
 *   <li><b>启动时预注册</b>：七个计数器在 Bean 构造时以 0 注册到 MeterRegistry，
 *       Grafana 面板不会出现 "no data"；事件发生时 increment。</li>
 *   <li><b>命名规范</b>：点分式（Micrometer 惯例），Prometheus 侧自动转为下划线，
 *       counter 类型自动追加 _total 后缀，如 hmdp.mq.event.failed → hmdp_mq_event_failed_total。</li>
 *   <li><b>与压测的关系</b>：这七个指标是"压测报告的一致性体检表"数据源——
 *       压测后应看到主链路错误计数为 0、限流拒绝计数符合预期。</li>
 * </ol></p>
 *
 * <p>指标全表（名称 → 含义 → 埋点位置）：
 * <pre>
 *   hmdp.mq.event.persisted      MQ 发送失败落本地消息表        RocketMqReliablePublisher.persistFailure
 *   hmdp.mq.event.failed         重试耗尽转 FAILED 终态          MqEventRetryJob（批次1 ⑤）
 *   hmdp.rollback.failure        Redis 回滚失败落库              RollbackFailureService（批次1 ④）
 *   hmdp.recovery.manual.review  恢复任务转人工审核              OrderCreateRecoveryService.moveToManualReview
 *   hmdp.reconcile.anomaly       对账扫描产生的异常工单          SeckillReconcileScanService.createTaskIfAbsent
 *   hmdp.ratelimit.rejected      滑动窗口限流拒绝请求            SlidingWindowInterceptor（批次1 ①）
 *   hmdp.consume.batch.error     消费端批量处理单条异常          SeckillPlusOrderListener 批量 catch 分支
 * </pre></p>
 */
@Component
public class MetricsRecorder {

    private final Counter mqEventPersisted;
    private final Counter mqEventFailed;
    private final Counter rollbackFailure;
    private final Counter recoveryManualReview;
    private final Counter reconcileAnomaly;
    private final Counter ratelimitRejected;
    private final Counter consumeBatchError;

    public MetricsRecorder(MeterRegistry registry) {
        this.mqEventPersisted = counter(registry, "hmdp.mq.event.persisted", "MQ发送失败落本地消息表次数");
        this.mqEventFailed = counter(registry, "hmdp.mq.event.failed", "本地消息表重试耗尽转FAILED终态次数");
        this.rollbackFailure = counter(registry, "hmdp.rollback.failure", "Redis库存回滚失败落库次数");
        this.recoveryManualReview = counter(registry, "hmdp.recovery.manual.review", "恢复任务转人工审核次数");
        this.reconcileAnomaly = counter(registry, "hmdp.reconcile.anomaly", "对账扫描产生的异常工单数");
        this.ratelimitRejected = counter(registry, "hmdp.ratelimit.rejected", "滑动窗口限流拒绝请求数");
        this.consumeBatchError = counter(registry, "hmdp.consume.batch.error", "消费端批量处理单条异常次数");
    }

    private static Counter counter(MeterRegistry registry, String name, String description) {
        return Counter.builder(name).description(description).register(registry);
    }

    /** MQ发送失败并已落本地消息表（生产端兜底触发） */
    public void incMqEventPersisted() {
        mqEventPersisted.increment();
    }

    /** 本地消息表重试达到上限，转 FAILED 终态等待人工 */
    public void incMqEventFailed() {
        mqEventFailed.increment();
    }

    /** Redis 库存回滚失败并已落 tb_rollback_failure_log */
    public void incRollbackFailure() {
        rollbackFailure.increment();
    }

    /** 恢复任务重试耗尽/拿到终态失败，转人工审核 */
    public void incRecoveryManualReview() {
        recoveryManualReview.increment();
    }

    /** 对账双向扫描产生一条异常工单（查重后新建时计数） */
    public void incReconcileAnomaly() {
        reconcileAnomaly.increment();
    }

    /** 滑动窗口限流拒绝一次秒杀请求（429） */
    public void incRatelimitRejected() {
        ratelimitRejected.increment();
    }

    /** 消费端批量处理中单条消息异常（转恢复任务的分支） */
    public void incConsumeBatchError() {
        consumeBatchError.increment();
    }
}
