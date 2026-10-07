package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.SeckillVoucherOrderMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.OrderCreateRecoveryTask;
import com.hmdp.mapper.OrderCreateRecoveryTaskMapper;
import com.hmdp.orderverify.model.OrderCreateRecoveryStatus;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.VoucherOrderCreateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderCreateRecoveryService {
    private final OrderCreateRecoveryTaskMapper taskMapper;
    private final IVoucherOrderService voucherOrderService;
    private final int maxRetryCount;
    private final int retryDelaySeconds;
    private final int manualReviewWindowMinutes;

    @Autowired
    public OrderCreateRecoveryService(OrderCreateRecoveryTaskMapper taskMapper,
                                      IVoucherOrderService voucherOrderService,
                                      @Value("${seckill.order-create-recovery.max-retry-count:5}") int maxRetryCount,
                                      @Value("${seckill.order-create-recovery.retry-delay-seconds:10}") int retryDelaySeconds,
                                      @Value("${seckill.order-create-recovery.manual-review-window-minutes:5}") int manualReviewWindowMinutes) {
        this.taskMapper = taskMapper;
        this.voucherOrderService = voucherOrderService;
        this.maxRetryCount = maxRetryCount;
        this.retryDelaySeconds = retryDelaySeconds;
        this.manualReviewWindowMinutes = manualReviewWindowMinutes;
    }

    public void createRecoveryTask(SeckillVoucherOrderMessage orderMessage, String sourceMessage, int reconsumeTimes) {
        taskMapper.insert(OrderCreateRecoveryTask.pending(
                "recover-order-" + orderMessage.getOrderId(),
                orderMessage.getOrderId(),
                orderMessage.getUserId(),
                orderMessage.getVoucherId(),
                orderMessage.getTraceId(),
                sourceMessage,
                maxRetryCount
        ).setLastMessage("MQ retries exhausted at reconsumeTimes=" + reconsumeTimes));
    }

    public void processDueTasks() {
        QueryWrapper<OrderCreateRecoveryTask> wrapper = new QueryWrapper<>();
        wrapper.in("task_status", OrderCreateRecoveryStatus.PENDING.name(), OrderCreateRecoveryStatus.RETRYING.name())
                .le("next_execute_time", LocalDateTime.now())
                .last("limit 20");
        List<OrderCreateRecoveryTask> tasks = taskMapper.selectList(wrapper);
        for (OrderCreateRecoveryTask task : tasks) {
            processSingleTask(task);
        }
    }

    public void abandonAndRollback(OrderCreateRecoveryTask task) {
        voucherOrderService.rollbackSeckillVoucherPlus(new VoucherOrder()
                .setId(task.getOrderId())
                .setUserId(task.getUserId())
                .setVoucherId(task.getVoucherId())
                .setTraceId(task.getTraceId()));
        task.setTaskStatus(OrderCreateRecoveryStatus.CLOSED.name());
        task.setLastMessage("manual review abandoned order and rolled back reservation");
        touch(task);
        taskMapper.updateById(task);
    }

    public void markManualSuccess(OrderCreateRecoveryTask task, String message) {
        task.setTaskStatus(OrderCreateRecoveryStatus.SUCCESS.name());
        task.setLastMessage(message);
        touch(task);
        taskMapper.updateById(task);
    }

    void processSingleTask(OrderCreateRecoveryTask task) {
        markProcessing(task);
        SeckillVoucherOrderMessage message = JSONUtil.toBean(task.getSourceMessage(), SeckillVoucherOrderMessage.class);
        try {
            VoucherOrderCreateResult result = voucherOrderService.createVoucherOrderPlus(message);
            if (result == VoucherOrderCreateResult.CREATED || result == VoucherOrderCreateResult.DUPLICATE) {
                task.setTaskStatus(OrderCreateRecoveryStatus.SUCCESS.name());
                task.setLastMessage("recovery create order succeeded");
                touch(task);
                taskMapper.updateById(task);
                return;
            }
            moveToManualReview(task, "recovery received terminal business result: " + result.name());
        } catch (Exception e) {
            int nextRetryCount = task.getRetryCount() + 1;
            task.setRetryCount(nextRetryCount);
            if (nextRetryCount > task.getMaxRetryCount()) {
                moveToManualReview(task, "recovery exhausted after infrastructure failure");
                return;
            }
            task.setTaskStatus(OrderCreateRecoveryStatus.RETRYING.name());
            task.setLastMessage("recovery failed, waiting next retry");
            task.setNextExecuteTime(LocalDateTime.now().plusSeconds(retryDelaySeconds));
            touch(task);
            taskMapper.updateById(task);
        }
    }

    private void markProcessing(OrderCreateRecoveryTask task) {
        task.setTaskStatus(OrderCreateRecoveryStatus.PROCESSING.name());
        task.setLastMessage("processing recovery order create task");
        touch(task);
        taskMapper.updateById(task);
    }

    private void moveToManualReview(OrderCreateRecoveryTask task, String message) {
        task.setTaskStatus(OrderCreateRecoveryStatus.MANUAL_REVIEW.name());
        task.setLastMessage(message);
        task.setAlarmStatus("PENDING");
        task.setManualDeadline(LocalDateTime.now().plusMinutes(manualReviewWindowMinutes));
        touch(task);
        taskMapper.updateById(task);
    }

    private void touch(OrderCreateRecoveryTask task) {
        LocalDateTime now = LocalDateTime.now();
        task.setUpdatedAt(now);
        task.setUpdateTime(now);
    }
}
