package com.hmdp.shopreview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_shop_review")
public class ShopReview implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
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
