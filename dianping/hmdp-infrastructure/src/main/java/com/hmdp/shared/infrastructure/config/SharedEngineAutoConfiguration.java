package com.hmdp.shared.infrastructure.config;

import com.hmdp.shared.domain.compensation.machine.FlashPayStateMachineDefinition;
import com.hmdp.shared.domain.compensation.service.FlashPayStateMachineFacade;
import com.hmdp.shared.stateengine.policy.RetryPolicy;
import com.hmdp.shared.stateengine.service.StateEngine;
import com.hmdp.shared.stateengine.service.StateMachineRegistry;
import com.hmdp.shared.stateengine.spi.StateTaskRepository;
import com.hmdp.shared.stateengine.spi.StateTransitionLogRepository;
import com.hmdp.shared.domain.taskchain.model.TaskNodeHandler;
import com.hmdp.shared.domain.taskchain.model.TaskNodeRegistry;
import com.hmdp.shared.domain.taskchain.service.PhaseExecutionEngine;
import com.hmdp.shared.domain.taskchain.service.TaskChainEngine;
import com.hmdp.shared.domain.taskchain.service.TaskDispatcher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 共享引擎自动配置
 * <p>
 * 负责将任务链引擎和补偿流引擎的所有组件注册为 Spring Bean：
 * <ul>
 *   <li>RetryPolicy：从配置文件读取最大重试次数</li>
 *   <li>StateMachineRegistry / StateEngine：共享状态引擎运行时</li>
 *   <li>FlashPayStateMachineFacade：无忧闪赔场景门面</li>
 *   <li>TaskNodeRegistry：扫描所有 TaskNodeHandler Bean，按 group/order 注册</li>
 *   <li>TaskDispatcher：持有 CPU 和 IO 两个线程池</li>
 *   <li>PhaseExecutionEngine：阶段执行引擎，依赖 TaskDispatcher</li>
 *   <li>TaskChainEngine：任务链引擎，依赖 Registry 和 PhaseExecutionEngine</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableConfigurationProperties(CompensationRetryProperties.class)
public class SharedEngineAutoConfiguration {

    @Bean
    public RetryPolicy retryPolicy(CompensationRetryProperties properties) {
        return new RetryPolicy(properties.getMaxRetryCount());
    }

    @Bean
    public StateMachineRegistry stateMachineRegistry() {
        StateMachineRegistry registry = new StateMachineRegistry();
        registry.register(new FlashPayStateMachineDefinition());
        return registry;
    }

    @Bean
    public StateEngine stateEngine(StateMachineRegistry stateMachineRegistry, RetryPolicy retryPolicy,
                                   StateTaskRepository stateTaskRepository,
                                   StateTransitionLogRepository stateTransitionLogRepository) {
        return new StateEngine(
                stateMachineRegistry,
                stateTaskRepository,
                stateTransitionLogRepository,
                retryPolicy
        );
    }

    @Bean
    public FlashPayStateMachineFacade flashPayStateMachineFacade(StateEngine stateEngine,
                                                                 StateTaskRepository stateTaskRepository) {
        return new FlashPayStateMachineFacade(stateEngine, stateTaskRepository);
    }

    @Bean
    public TaskNodeRegistry taskNodeRegistry(List<TaskNodeHandler<?, ?>> handlers) {
        TaskNodeRegistry registry = new TaskNodeRegistry();
        registry.register(registry.wrapHandlers(handlers));
        return registry;
    }

    @Bean
    public TaskDispatcher taskDispatcher(@Qualifier("taskChainCpuExecutor") Executor cpuExecutor,
                                         @Qualifier("taskChainIoExecutor") Executor ioExecutor) {
        return new TaskDispatcher(cpuExecutor, ioExecutor);
    }

    @Bean
    public PhaseExecutionEngine phaseExecutionEngine(TaskDispatcher taskDispatcher) {
        return new PhaseExecutionEngine(taskDispatcher);
    }

    @Bean
    public TaskChainEngine taskChainEngine(TaskNodeRegistry taskNodeRegistry,
                                           PhaseExecutionEngine phaseExecutionEngine) {
        return new TaskChainEngine(taskNodeRegistry, phaseExecutionEngine);
    }
}
