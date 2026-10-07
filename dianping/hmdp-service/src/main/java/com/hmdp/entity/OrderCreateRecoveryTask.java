package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hmdp.orderverify.model.OrderCreateRecoveryStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("tb_order_create_recovery_task")
public class OrderCreateRecoveryTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private Long orderId;
    private Long userId;
    private Long voucherId;
    private String traceId;
    private String sourceMessage;
    private String taskStatus;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime nextExecuteTime;
    private String lastMessage;
    private LocalDateTime manualDeadline;
    private String alarmStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static OrderCreateRecoveryTask pending(String taskId,
                                                  Long orderId,
                                                  Long userId,
                                                  Long voucherId,
                                                  String traceId,
                                                  String sourceMessage,
                                                  int maxRetryCount) {
        LocalDateTime now = LocalDateTime.now();
        return new OrderCreateRecoveryTask()
                .setTaskId(taskId)
                .setOrderId(orderId)
                .setUserId(userId)
                .setVoucherId(voucherId)
                .setTraceId(traceId)
                .setSourceMessage(sourceMessage)
                .setTaskStatus(OrderCreateRecoveryStatus.PENDING.name())
                .setRetryCount(0)
                .setMaxRetryCount(maxRetryCount)
                .setNextExecuteTime(now)
                .setLastMessage("MQ retries exhausted, waiting for local recovery")
                .setAlarmStatus("NONE")
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setCreateTime(now)
                .setUpdateTime(now);
    }
}
