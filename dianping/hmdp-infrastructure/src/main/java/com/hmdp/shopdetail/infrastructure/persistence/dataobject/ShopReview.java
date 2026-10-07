package com.hmdp.shopdetail.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店铺评价数据库映射对象
 * <p>
 * 对应 tb_shop_review 表，由 {@link com.hmdp.shopdetail.infrastructure.persistence.mapper.ShopDetailReviewMapper} 操作。
 * tags 字段在数据库中以逗号分隔的字符串存储，转换时拆分为 List。
 * </p>
 */
@Data
@TableName("tb_shop_review")
public class ShopReview {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private Long userId;
    private Integer overallScore;
    private Integer tasteScore;
    private Integer environmentScore;
    private Integer serviceScore;
    private String content;
    private String tags;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
