package com.hmdp.shopdetail.application.task;

import com.hmdp.shopdetail.domain.model.ShopDetailAggregate;
import com.hmdp.shopdetail.domain.model.ShopMarketingOffer;
import com.hmdp.shopdetail.domain.model.ShopReview;
import com.hmdp.shopdetail.domain.model.ShopReviewSummary;
import com.hmdp.shopdetail.domain.model.ShopVisitBlog;
import com.hmdp.shared.domain.taskchain.annotation.TaskNode;
import com.hmdp.shared.domain.taskchain.enums.ProcessPhase;
import com.hmdp.shared.domain.taskchain.enums.TaskTypeEnum;
import com.hmdp.shared.domain.taskchain.model.TaskContext;
import com.hmdp.shared.domain.taskchain.model.TaskNodeHandler;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 任务链节点：组装商详聚合根
 * <p>
 * POST_PROCESSING 阶段，order=60，CPU 密集型。
 * 从 context 中读取各并行节点写入的属性（base/blogs/vouchers/reviews/reviewSummary 等），
 * 填充到 {@link ShopDetailAggregate} 并写入 context.output，供应用层取用。
 * </p>
 */
@Component
@TaskNode(group = "shop_detail", nodeId = "assemble_shop_detail", nodeName = "组装商详聚合", phase = ProcessPhase.POST_PROCESSING, order = 60, taskType = TaskTypeEnum.CPU_BOUND)
public class AssembleShopDetailNode implements TaskNodeHandler<Long, ShopDetailAggregate> {

    @Override
    public void execute(TaskContext<Long, ShopDetailAggregate> context) {
        ShopDetailAggregate aggregate = context.getAttribute("base");
        if (aggregate == null) {
            // 没查到基础店铺时仍返回一个带 shopId 的空聚合，避免后续流程空指针。
            aggregate = new ShopDetailAggregate();
            aggregate.setShopId(context.getInput());
        }
        List<?> blogs = context.getAttribute("blogs");
        List<?> vouchers = context.getAttribute("vouchers");
        List<ShopReview> reviews = context.getAttribute("reviews");
        ShopReviewSummary summary = context.getAttribute("reviewSummary");
        // 并行节点只负责把原始片段塞进 context，这里统一收口成一个完整聚合根。
        // 这样做的好处是：节点之间不互相持有引用，应用层可以自由调整并行策略。
        aggregate.setBlogs(blogs == null ? Collections.<ShopVisitBlog>emptyList() : (List<ShopVisitBlog>) blogs);
        aggregate.setVouchers(vouchers == null ? Collections.<ShopMarketingOffer>emptyList() : (List<ShopMarketingOffer>) vouchers);
        aggregate.setReviews(reviews == null ? Collections.<ShopReview>emptyList() : reviews);
        aggregate.setReviewSummary(summary);
        context.setOutput(aggregate);
    }
}
