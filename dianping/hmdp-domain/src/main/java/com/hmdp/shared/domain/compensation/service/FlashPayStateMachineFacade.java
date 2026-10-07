package com.hmdp.shared.domain.compensation.service;

import com.hmdp.shared.domain.compensation.machine.FlashPayStateMachineDefinition;
import com.hmdp.shared.domain.compensation.model.FlashPayPayload;
import com.hmdp.shared.domain.compensation.model.FlashPayStateCode;
import com.hmdp.shared.stateengine.model.StateTask;
import com.hmdp.shared.stateengine.policy.RetryPolicy;
import com.hmdp.shared.stateengine.service.StateEngine;
import com.hmdp.shared.stateengine.service.StateMachineRegistry;
import com.hmdp.shared.stateengine.spi.InMemoryStateTaskRepository;
import com.hmdp.shared.stateengine.spi.InMemoryStateTransitionLogRepository;

/**
 * 无忧闪赔状态机门面 — 闪赔状态机的业务入口。
 *
 * <p>定位：无忧闪赔状态机层。Facade 是外部调用状态机的唯一入口，
 * 负责组装 StateTask 并调用 StateEngine。</p>
 *
 * <p>与秒杀对账不同：闪赔状态机使用内存存储（InMemory），
 * 不持久化到数据库，适用于低延迟的同步赔付场景。</p>
 *
 * <p>{@link #createDefault()} 工厂方法提供了一个开箱即用的
 * 默认配置实例（内存在存 + 最大3次重试），方便测试和简单场景使用。</p>
 */
public class FlashPayStateMachineFacade {

    private final StateEngine stateEngine;

    public FlashPayStateMachineFacade(StateEngine stateEngine) {
        this.stateEngine = stateEngine;
    }

    /**
     * 创建默认配置的 Facade 实例。
     *
     * <p>默认配置：
     * <ul>
     *   <li>存储：InMemory（内存存储，不持久化）</li>
     *   <li>重试：最大3次</li>
     *   <li>注册：自动注册 FlashPayStateMachineDefinition</li>
     * </ul></p>
     */
    public static FlashPayStateMachineFacade createDefault() {
        StateMachineRegistry registry = new StateMachineRegistry();
        registry.register(new FlashPayStateMachineDefinition());
        return new FlashPayStateMachineFacade(new StateEngine(
                registry,
                new InMemoryStateTaskRepository(),
                new InMemoryStateTransitionLogRepository(),
                new RetryPolicy(3)
        ));
    }

    /**
     * 启动闪赔状态机。
     *
     * @param claimNo 理赔单号（同时作为 taskId）
     * @param shopId  店铺ID（同时作为 bizKey）
     * @param payload 闪赔参数（planCode + reason）
     * @return 执行完毕后的 StateTask（COMPLETED 或 MANUAL_REVIEW）
     */
    public StateTask start(String claimNo, String shopId, FlashPayPayload payload) {
        return stateEngine.start(StateTask.create(claimNo, shopId, FlashPayStateCode.MACHINE_CODE, FlashPayStateCode.SUBMITTED.name(), payload.serialize()));
    }
}
