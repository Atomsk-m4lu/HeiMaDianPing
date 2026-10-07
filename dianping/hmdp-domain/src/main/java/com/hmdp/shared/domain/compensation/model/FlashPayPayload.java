package com.hmdp.shared.domain.compensation.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 无忧闪赔 Payload — 闪赔状态机任务的业务参数载体。
 *
 * <p>定位：无忧闪赔状态机层。封装闪赔申请所需的业务上下文，
 * 序列化为字符串存储到 StateTask.payload 中，执行时反序列化还原。</p>
 *
 * <p>序列化格式（用 | 分隔）：
 * <pre>planCode|reason</pre></p>
 */
@Getter
@AllArgsConstructor
public class FlashPayPayload {

    /** 闪赔计划编码 */
    private final String planCode;
    /** 申诉原因（用户填写，SUBMITTED节点校验非空） */
    private final String reason;

    /** 序列化为字符串，存入 StateTask.payload */
    public String serialize() {
        return planCode + "|" + (reason == null ? "" : reason);
    }

    /** 从 StateTask.payload 反序列化还原 */
    public static FlashPayPayload deserialize(String payload) {
        String[] parts = payload.split("\\|", 2);
        return new FlashPayPayload(parts[0], parts.length > 1 ? parts[1] : "");
    }
}
