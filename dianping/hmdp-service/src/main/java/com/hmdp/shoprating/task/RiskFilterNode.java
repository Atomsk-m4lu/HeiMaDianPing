package com.hmdp.shoprating.task;

import cn.hutool.core.util.StrUtil;
import com.hmdp.entity.Blog;
import com.hmdp.shared.domain.taskchain.annotation.TaskNode;
import com.hmdp.shared.domain.taskchain.enums.ProcessPhase;
import com.hmdp.shared.domain.taskchain.enums.TaskTypeEnum;
import com.hmdp.shared.domain.taskchain.model.TaskContext;
import com.hmdp.shared.domain.taskchain.model.TaskNodeHandler;
import com.hmdp.shoprating.domain.model.ShopRatingRefreshMessage;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@TaskNode(
        nodeId = "risk_filter",
        group = "shop_rating_refresh",
        phase = ProcessPhase.PRE_VALIDATION,
        order = 20,
        taskType = TaskTypeEnum.CPU_BOUND
)
public class RiskFilterNode implements TaskNodeHandler<ShopRatingRefreshMessage, Void> {

    @Override
    public void execute(TaskContext<ShopRatingRefreshMessage, Void> context) {
        List<Blog> rawBlogs = context.getAttribute("rawBlogs");
        List<Blog> validBlogs = rawBlogs == null ? Collections.emptyList() : rawBlogs.stream()
                .filter(blog -> StrUtil.isNotBlank(blog.getContent()) || StrUtil.isNotBlank(blog.getImages()))
                .collect(Collectors.toList());
        context.setAttribute("validBlogs", validBlogs);
        if (validBlogs.isEmpty()) {
            context.markTerminate();
        }
    }
}
