package com.hmdp.shared.domain.taskchain.annotation;

import com.hmdp.shared.domain.taskchain.enums.ProcessPhase;
import com.hmdp.shared.domain.taskchain.enums.TaskTypeEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 任务链节点注解
 * <p>
 * 标注在实现了 {@link com.hmdp.shared.domain.taskchain.model.TaskNodeHandler} 的类上，
 * 声明该节点所属的分组、执行阶段、顺序、任务类型等元数据。
 * 引擎启动时由 {@link com.hmdp.shared.domain.taskchain.model.TaskNodeRegistry} 扫描并注册。
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TaskNode {

    /** 节点唯一 ID，同一 group 内不可重复 */
    String nodeId();

    /** 节点名称，用于日志和监控展示 */
    String nodeName() default "";

    /** 任务类型：IO_BOUND 使用 IO 线程池，CPU_BOUND 使用 CPU 线程池 */
    TaskTypeEnum taskType() default TaskTypeEnum.IO_BOUND;

    /** 节点所属分组，同一分组的节点会被一起编排执行 */
    String group();

    /** 同一阶段内的执行顺序，数字越小越先执行 */
    int order() default 0;

    /** 是否可跳过：true 时节点执行失败不会终止整条链路 */
    boolean skippable() default false;

    /** 节点描述，仅用于文档说明 */
    String description() default "";

    /** 节点所属执行阶段 */
    ProcessPhase phase() default ProcessPhase.DATA_PROCESSING;

    /** 节点超时时间（毫秒），0 表示不限制 */
    long timeout() default 0L;
}
