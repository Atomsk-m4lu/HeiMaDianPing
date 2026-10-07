package com.hmdp.shared.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 状态机转换日志数据库映射对象
 * <p>
 * 对应 tb_state_transition_log 表，由 {@link com.hmdp.shared.infrastructure.persistence.mapper.StateTransitionLogMapper} 操作。
 * </p>
 */
@Data
@TableName("tb_state_transition_log")
public class StateTransitionLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private String machineCode;
    private String fromState;
    private String toState;
    private Integer success;
    private String errorCode;
    private String message;
    private Long durationMs;
    private LocalDateTime createdAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
