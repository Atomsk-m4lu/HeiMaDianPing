package com.hmdp.shopdetail.domain.repository;

import com.hmdp.shopdetail.domain.model.GuaranteeClaim;

import java.util.Optional;

/**
 * 保障申诉仓储接口（Domain 层定义，Infrastructure 层实现）
 * <p>
 * 实现类为 {@link com.hmdp.shopdetail.infrastructure.repository.MybatisGuaranteeClaimRepository}。
 * </p>
 */
public interface GuaranteeClaimRepository {

    /** 新增或更新申诉单（id 为 null 时 insert，否则 update） */
    GuaranteeClaim save(GuaranteeClaim claim);

    /** 按申诉单号查询，不存在时返回 empty */
    Optional<GuaranteeClaim> findByClaimNo(String claimNo);
}
