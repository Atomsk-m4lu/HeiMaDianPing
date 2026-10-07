package com.hmdp.shoprating.task;

import com.hmdp.entity.Blog;
import com.hmdp.shared.domain.taskchain.annotation.TaskNode;
import com.hmdp.shared.domain.taskchain.enums.ProcessPhase;
import com.hmdp.shared.domain.taskchain.enums.TaskTypeEnum;
import com.hmdp.shared.domain.taskchain.model.TaskContext;
import com.hmdp.shared.domain.taskchain.model.TaskNodeHandler;
import com.hmdp.shoprating.domain.model.ShopRatingRefreshMessage;
import com.hmdp.shoprating.service.ShopRatingCalculator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@TaskNode(
        nodeId = "calc_weighted_score",
        group = "shop_rating_refresh",
        phase = ProcessPhase.DATA_PROCESSING,
        order = 10,
        taskType = TaskTypeEnum.CPU_BOUND
)
public class CalcWeightedScoreNode implements TaskNodeHandler<ShopRatingRefreshMessage, Void> {

    private final ShopRatingCalculator calculator;

    public CalcWeightedScoreNode(ShopRatingCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public void execute(TaskContext<ShopRatingRefreshMessage, Void> context) {
        List<Blog> validBlogs = context.getAttribute("validBlogs");
        int calculatedScore = calculator.calculateScore(validBlogs);
        context.setAttribute("calculatedScore", calculatedScore);
        context.setAttribute("commentsCount", validBlogs == null ? 0 : validBlogs.size());
    }
}
