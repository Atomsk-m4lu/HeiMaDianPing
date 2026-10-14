package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.RollbackFailureLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存回滚失败日志 Mapper —— 落库与告警扫描（alert_status=PENDING）。
 */
@Mapper
public interface RollbackFailureLogMapper extends BaseMapper<RollbackFailureLog> {
}
