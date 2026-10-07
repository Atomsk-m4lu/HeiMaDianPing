package com.hmdp.shopdetail.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 保障申诉实体
 * <p>
 * 用户针对到店体验异常发起的申诉单，对应"无忧闪赔"计划。
 * 申诉提交后由 {@link com.hmdp.shared.domain.compensation.service.FlashPayStateMachineFacade}
 * 驱动状态机流转，自动完成或转人工审核。
 * </p>
 */
@Data
public class GuaranteeClaim {

    private Long id;
    private Long shopId;
    private Long userId;
    private String claimNo;
    private String planCode;
    private String reason;
    private String status;
    private String resultMessage;
    private LocalDateTime createTime;
}
