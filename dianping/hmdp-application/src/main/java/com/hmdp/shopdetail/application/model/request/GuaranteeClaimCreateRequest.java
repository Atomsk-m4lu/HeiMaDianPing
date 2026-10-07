package com.hmdp.shopdetail.application.model.request;

import lombok.Data;

@Data
public class GuaranteeClaimCreateRequest {

    private Long shopId;
    private String planCode;
    private String reason;
}
