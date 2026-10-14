-- 批次1 ④：库存回滚失败日志表
-- 执行方式（VM 或本机均可）：mysql -uroot -p dingping < patch_rollback_failure_log.sql
CREATE TABLE IF NOT EXISTS `tb_rollback_failure_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `voucher_id` bigint NOT NULL COMMENT '券ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT 'Redis trace ID',
  `source` varchar(32) NOT NULL COMMENT '触发来源：MAIN_CHAIN/LISTENER',
  `result_code` varchar(64) DEFAULT NULL COMMENT '回滚结果码/异常摘要',
  `retry_attempts` int NOT NULL DEFAULT '0' COMMENT '回滚尝试次数',
  `detail` varchar(1024) DEFAULT NULL COMMENT '异常详情（截断1000字符）',
  `alert_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '告警状态：PENDING/HANDLED',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_order_id` (`order_id`) USING BTREE,
  KEY `idx_alert_status` (`alert_status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
