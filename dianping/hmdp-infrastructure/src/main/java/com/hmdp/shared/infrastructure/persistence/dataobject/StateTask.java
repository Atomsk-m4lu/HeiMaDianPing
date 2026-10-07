package com.hmdp.shared.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 状态机任务数据库映射对象
 * <p>
 * 对应 tb_state_task 表，由 {@link com.hmdp.shared.infrastructure.persistence.mapper.StateTaskMapper} 操作。
 * </p>
 */
@Data
@TableName("tb_state_task")
public class StateTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private String bizKey;
    private String machineCode;
    private String payload;
    private String currentState;
    private String runtimeStatus;
    private String lastMessage;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
