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
import com.hmdp.shared.stateengine.spi.StateTaskRepository;

/**
 * 无忧闪赔状态机门面 — 闪赔状态机的业务入口。
 *
 * <p>定位：无忧闪赔状态机层。Facade 是外部调用状态机的唯一入口，
 * 负责组装 StateTask 并调用 StateEngine。</p>
 *
 * <p>存储方案由装配决定：Spring 生产装配注入 MyBatis 仓储（任务落 tb_state_task，支持跨进程恢复）；
 * {@link #createDefault()} 提供内存版开箱即用装配（测试/简单场景）。</p>
 */
public class FlashPayStateMachineFacade {

    private final StateEngine stateEngine;

    /** 任务仓储（与 StateEngine 同一份）：重试入口用它查已有任务，决定 resume 还是 start */
    private final StateTaskRepository taskRepository;

    public FlashPayStateMachineFacade(StateEngine stateEngine, StateTaskRepository taskRepository) {
        this.stateEngine = stateEngine;
        this.taskRepository = taskRepository;
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
        StateTaskRepository taskRepository = new InMemoryStateTaskRepository();
        return new FlashPayStateMachineFacade(new StateEngine(
                registry,
                taskRepository,
                new InMemoryStateTransitionLogRepository(),
                new RetryPolicy(3)
        ), taskRepository);
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

    /**
     * 启动或恢复闪赔状态机 —— 重试入口专用。
     *
     * <p>为什么不能直接用 {@link #start}：start 走 save（insert），同一 claimNo
     * 第二次提交会撞 tb_state_task 的 uk_task_id 唯一键（2026-10-14 实测复现：
     * Duplicate entry ... for key 'tb_state_task.uk_task_id'）。
     * 即便绕过约束，重建任务也会把进度/重试计数清零——与"断点续传"的设计相悖。</p>
     *
     * <p>语义：无任务 → start 新建；已有任务 → resume 从持久化的当前状态续跑。
     * 对 MANUAL_REVIEW 任务，续跑进入 ManualReviewNode 并以 terminalSuccess 收口
     * （解释为"人工处理完毕、从状态机视角关闭"，人工结果由外部系统处理）。</p>
     */
    public StateTask startOrResume(String claimNo, String shopId, FlashPayPayload payload) {
        StateTask existing = taskRepository.findByTaskId(claimNo);
        if (existing == null) {
            return start(claimNo, shopId, payload);
        }
        return stateEngine.resume(existing);
    }
}
