package com.hmdp.shared.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.shared.infrastructure.persistence.mapper.StateTaskMapper;
import com.hmdp.shared.stateengine.model.StateTask;
import com.hmdp.shared.stateengine.model.StateTaskStatus;
import com.hmdp.shared.stateengine.spi.StateTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

/**
 * 状态机任务仓储 MyBatis 实现
 * <p>
 * 通过 MyBatis Plus 操作 tb_state_task 表。
 * </p>
 */
@Slf4j
@Repository
public class MybatisStateTaskRepository implements StateTaskRepository {

    @Resource
    private StateTaskMapper stateTaskMapper;

    @Override
    public void save(StateTask task) {
        stateTaskMapper.insert(toDataObject(task));
    }

    @Override
    public void update(StateTask task) {
        QueryWrapper<com.hmdp.shared.infrastructure.persistence.dataobject.StateTask> wrapper = new QueryWrapper<>();
        wrapper.eq("task_id", task.getTaskId());
        com.hmdp.shared.infrastructure.persistence.dataobject.StateTask existing = stateTaskMapper.selectOne(wrapper);
        if (existing != null) {
            com.hmdp.shared.infrastructure.persistence.dataobject.StateTask dataObject = toDataObject(task);
            dataObject.setId(existing.getId());
            // 条件更新：where task_id=? and runtime_status=旧状态 —— 防止并发 resume 互相覆盖
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<com.hmdp.shared.infrastructure.persistence.dataobject.StateTask> uw =
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            uw.eq("task_id", task.getTaskId())
                    .eq("runtime_status", existing.getRuntimeStatus())
                    .set("current_state", dataObject.getCurrentState())
                    .set("runtime_status", dataObject.getRuntimeStatus())
                    .set("last_message", dataObject.getLastMessage())
                    .set("retry_count", dataObject.getRetryCount())
                    .set("updated_at", dataObject.getUpdatedAt());
            int updated = stateTaskMapper.update(null, uw);
            if (updated == 0) {
                log.warn("state task concurrent update skipped, taskId={}, expectedRuntimeStatus={}",
                        task.getTaskId(), existing.getRuntimeStatus());
            }
        } else {
            stateTaskMapper.insert(toDataObject(task));
        }
    }

    @Override
    public StateTask findByTaskId(String taskId) {
        QueryWrapper<com.hmdp.shared.infrastructure.persistence.dataobject.StateTask> wrapper = new QueryWrapper<>();
        wrapper.eq("task_id", taskId);
        com.hmdp.shared.infrastructure.persistence.dataobject.StateTask dataObject = stateTaskMapper.selectOne(wrapper);
        if (dataObject == null) {
            return null;
        }
        return StateTask.restore(
                dataObject.getTaskId(),
                dataObject.getBizKey(),
                dataObject.getMachineCode(),
                dataObject.getPayload(),
                dataObject.getCurrentState(),
                StateTaskStatus.valueOf(dataObject.getRuntimeStatus()),
                dataObject.getLastMessage(),
                dataObject.getRetryCount(),
                dataObject.getCreatedAt(),
                dataObject.getUpdatedAt()
        );
    }

    private com.hmdp.shared.infrastructure.persistence.dataobject.StateTask toDataObject(StateTask task) {
        com.hmdp.shared.infrastructure.persistence.dataobject.StateTask dataObject = new com.hmdp.shared.infrastructure.persistence.dataobject.StateTask();
        dataObject.setTaskId(task.getTaskId());
        dataObject.setBizKey(task.getBizKey());
        dataObject.setMachineCode(task.getMachineCode());
        dataObject.setPayload(task.getPayload());
        dataObject.setCurrentState(task.getCurrentState());
        dataObject.setRuntimeStatus(task.getRuntimeStatus().name());
        dataObject.setLastMessage(task.getLastMessage());
        dataObject.setRetryCount(task.getRetryCount());
        dataObject.setCreatedAt(task.getCreatedAt());
        dataObject.setUpdatedAt(task.getUpdatedAt());
        return dataObject;
    }
}
