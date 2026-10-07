package com.hmdp.service.impl;

import com.hmdp.dto.SeckillVoucherOrderMessage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.OrderCreateVerifyTask;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.VoucherReconcileLog;
import com.hmdp.mapper.OrderCreateVerifyTaskMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mapper.VoucherReconcileLogMapper;
import com.hmdp.orderverify.model.OrderCreateVerifyStatus;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.shared.reconciliation.model.ReconcileLogType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderCreateVerificationService {

    private final OrderCreateVerifyTaskMapper taskMapper;
    private final VoucherOrderMapper voucherOrderMapper;
    private final VoucherReconcileLogMapper voucherReconcileLogMapper;
    private final IVoucherOrderService voucherOrderService;
    private final OrderCreateRecoveryService orderCreateRecoveryService;
    private final int maxRetryCount;
    private final int retryDelaySeconds;

    @Autowired
    public OrderCreateVerificationService(OrderCreateVerifyTaskMapper taskMapper,
                                          VoucherOrderMapper voucherOrderMapper,
                                          VoucherReconcileLogMapper voucherReconcileLogMapper,
                                          IVoucherOrderService voucherOrderService) {
        this(taskMapper, voucherOrderMapper, voucherReconcileLogMapper, voucherOrderService, null, 5, 10);
    }

    public OrderCreateVerificationService(OrderCreateVerifyTaskMapper taskMapper,
                                          VoucherOrderMapper voucherOrderMapper,
                                          VoucherReconcileLogMapper voucherReconcileLogMapper,
                                          IVoucherOrderService voucherOrderService,
                                          OrderCreateRecoveryService orderCreateRecoveryService,
                                          int maxRetryCount,
                                          int retryDelaySeconds) {
        this.taskMapper = taskMapper;
        this.voucherOrderMapper = voucherOrderMapper;
        this.voucherReconcileLogMapper = voucherReconcileLogMapper;
        this.voucherOrderService = voucherOrderService;
        this.orderCreateRecoveryService = orderCreateRecoveryService;
        this.maxRetryCount = maxRetryCount;
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public void createPendingTask(Long orderId, Long userId, Long voucherId, String traceId) {
        taskMapper.insert(OrderCreateVerifyTask.pending(
                "verify-order-" + orderId,
                orderId,
                userId,
                voucherId,
                traceId,
                maxRetryCount
        ));
    }

    public void verifyDueTasks() {
        QueryWrapper<OrderCreateVerifyTask> wrapper = new QueryWrapper<>();
        wrapper.in("task_status", OrderCreateVerifyStatus.PENDING.name(), OrderCreateVerifyStatus.RETRYING.name())
                .le("next_check_time", LocalDateTime.now())
                .last("limit 20");
        List<OrderCreateVerifyTask> tasks = taskMapper.selectList(wrapper);
        for (OrderCreateVerifyTask task : tasks) {
            verifySingleTask(task);
        }
    }

    void verifySingleTask(OrderCreateVerifyTask task) {
        markVerifying(task);

        VoucherOrder order = voucherOrderMapper.selectById(task.getOrderId());
        VoucherReconcileLog deductLog = voucherReconcileLogMapper.selectOne(new QueryWrapper<VoucherReconcileLog>()
                .eq("trace_id", task.getTraceId())
                .eq("log_type", ReconcileLogType.DEDUCT)
                .last("limit 1"));

        if (order != null && deductLog != null) {
            task.setTaskStatus(OrderCreateVerifyStatus.SUCCESS.name());
            task.setLastMessage("order and deduct log verified");
            touch(task);
            taskMapper.updateById(task);
            return;
        }

        int nextRetryCount = task.getRetryCount() + 1;
        task.setRetryCount(nextRetryCount);
        touch(task);

        if (nextRetryCount > task.getMaxRetryCount()) {
            task.setTaskStatus(OrderCreateVerifyStatus.FAILED.name());
            task.setLastMessage("verification exhausted, waiting for recovery or manual review");
            taskMapper.updateById(task);
            if (orderCreateRecoveryService != null) {
                SeckillVoucherOrderMessage message = new SeckillVoucherOrderMessage();
                message.setOrderId(task.getOrderId());
                message.setUserId(task.getUserId());
                message.setVoucherId(task.getVoucherId());
                message.setTraceId(task.getTraceId());
                orderCreateRecoveryService.createRecoveryTask(
                        message,
                        cn.hutool.json.JSONUtil.toJsonStr(message),
                        nextRetryCount
                );
            }
            return;
        }

        task.setTaskStatus(OrderCreateVerifyStatus.RETRYING.name());
        task.setLastMessage("order or deduct log not ready, wait next verification");
        task.setNextCheckTime(LocalDateTime.now().plusSeconds(retryDelaySeconds));
        taskMapper.updateById(task);
    }

    private void markVerifying(OrderCreateVerifyTask task) {
        task.setTaskStatus(OrderCreateVerifyStatus.VERIFYING.name());
        task.setLastMessage("verifying order create result");
        touch(task);
        taskMapper.updateById(task);
    }

    private void touch(OrderCreateVerifyTask task) {
        LocalDateTime now = LocalDateTime.now();
        task.setUpdatedAt(now);
        task.setUpdateTime(now);
    }
}
