package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillVoucherOrderMessage;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    Result seckillVoucherPlus(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);

    VoucherOrderCreateResult createVoucherOrderPlus(SeckillVoucherOrderMessage message);

    void rollbackSeckillVoucherPlus(VoucherOrder voucherOrder);

    boolean existsVoucherOrder(Long orderId);

    void handleVoucherOrder(VoucherOrder voucherOrder);
}
