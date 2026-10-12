package com.hmdp.shared.stateengine.spi;

import com.hmdp.shared.stateengine.model.StateTransitionLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 状态流转日志仓库 — 内存实现。
 *
 * <p>定位：核心引擎层。CBB 提供的 {@link StateTransitionLogRepository} 默认实现，
 * 基于 ArrayList 顺序存储 StateTransitionLog，每次 StateNode.execute() 执行后
 * StateEngine 都会追加一条日志到此仓库。</p>
 *
 * <p>适用场景：测试环境 / 低延迟同步场景（同 {@link InMemoryStateTaskRepository}）。
 * JVM 重启后日志丢失，需持久化审计轨迹时应替换为 DB 实现。</p>
 */
public class InMemoryStateTransitionLogRepository implements StateTransitionLogRepository {

    /** 流转日志顺序存储列表，按 add 时间先后排列 */
    private final List<StateTransitionLog> storage = new ArrayList<>();

    /**
     * {@inheritDoc}
     * <p>追加写入到 ArrayList 末尾，保持时间顺序。</p>
     */
    @Override
    public void save(StateTransitionLog transitionLog) {
        storage.add(transitionLog);
    }

    /**
     * 获取当前已存储的所有流转日志（只读视图）。
     * <p>内存实现特有的便捷方法，不在 {@link StateTransitionLogRepository} 接口中。
     * 主要用于单元测试中断言日志内容，不可修改返回的列表。</p>
     *
     * @return 流转日志的不可变列表视图
     */
    public List<StateTransitionLog> getStorage() {
        return Collections.unmodifiableList(storage);
    }
}