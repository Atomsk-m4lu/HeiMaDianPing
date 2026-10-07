package com.hmdp.shopdetail.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 保障申诉数据库映射对象
 * <p>
 * 对应 tb_shop_guarantee_claim 表，由 {@link com.hmdp.shopdetail.infrastructure.persistence.mapper.GuaranteeClaimMapper} 操作。
 * </p>
 */
@Data
@TableName("tb_shop_guarantee_claim")
public class GuaranteeClaim {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private Long userId;
    private String claimNo;
    private String planCode;
    private String reason;
    private String status;
    private String resultMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
