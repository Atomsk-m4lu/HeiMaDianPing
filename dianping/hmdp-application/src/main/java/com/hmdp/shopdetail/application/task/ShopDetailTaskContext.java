package com.hmdp.shopdetail.application.task;

import com.hmdp.shopdetail.domain.model.ShopDetailAggregate;
import com.hmdp.shared.domain.taskchain.model.TaskContext;

/**
 * 商详任务链上下文
 * <p>
 * 泛型参数固定为 Long（shopId）→ ShopDetailAggregate，
 * 各查询节点通过 setAttribute/getAttribute 共享中间数据，
 * 最终由 {@link AssembleShopDetailNode} 汇总到 output。
 * 可以把它理解成“本次商详查询的共享工作内存”：
 * base/blogs/vouchers/reviews/reviewSummary 都先写进这里，
 * 等并行阶段结束后再统一组装成聚合根。
 * </p>
 */
public class ShopDetailTaskContext extends TaskContext<Long, ShopDetailAggregate> {
}
