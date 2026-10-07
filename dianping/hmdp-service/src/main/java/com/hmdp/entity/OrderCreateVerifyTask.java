package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hmdp.orderverify.model.OrderCreateVerifyStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("tb_order_create_verify_task")
public class OrderCreateVerifyTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private Long orderId;
    private Long userId;
    private Long voucherId;
    private String traceId;
    private String eventType;
    private String taskStatus;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime nextCheckTime;
    private String lastMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static OrderCreateVerifyTask pending(String taskId,
                                                Long orderId,
                                                Long userId,
                                                Long voucherId,
                                                String traceId,
                                                int maxRetryCount) {
        LocalDateTime now = LocalDateTime.now();
        return new OrderCreateVerifyTask()
                .setTaskId(taskId)
                .setOrderId(orderId)
                .setUserId(userId)
                .setVoucherId(voucherId)
                .setTraceId(traceId)
                .setEventType("ORDER_CREATE_VERIFY")
                .setTaskStatus(OrderCreateVerifyStatus.PENDING.name())
                .setRetryCount(0)
                .setMaxRetryCount(maxRetryCount)
                .setNextCheckTime(now)
                .setLastMessage("pending first verification")
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setCreateTime(now)
                .setUpdateTime(now);
    }
}
