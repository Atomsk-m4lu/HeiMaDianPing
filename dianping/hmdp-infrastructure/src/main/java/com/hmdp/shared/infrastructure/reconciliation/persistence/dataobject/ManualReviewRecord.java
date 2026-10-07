package com.hmdp.shared.infrastructure.reconciliation.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hmdp.shared.reconciliation.model.ManualReviewResult;
import com.hmdp.shared.reconciliation.model.ManualReviewStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_manual_review_record")
public class ManualReviewRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private String sceneCode;
    private String bizKey;
    private ManualReviewStatus reviewStatus;
    private String assignee;
    private ManualReviewResult reviewResult;
    private String reviewComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
