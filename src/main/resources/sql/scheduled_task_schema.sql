-- 定时任务表
CREATE TABLE IF NOT EXISTS `scheduled_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_key` VARCHAR(64) NOT NULL COMMENT '任务唯一标识',
    `task_name` VARCHAR(128) NOT NULL COMMENT '任务名称',
    `task_group` VARCHAR(64) DEFAULT 'DEFAULT' COMMENT '任务分组',
    `cron_expression` VARCHAR(64) NOT NULL COMMENT 'Cron 表达式',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '任务描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-暂停, 1-运行',
    `retry_count` INT DEFAULT 0 COMMENT '当前重试次数',
    `max_retry_count` INT DEFAULT 3 COMMENT '最大重试次数',
    `retry_interval_seconds` INT DEFAULT 60 COMMENT '重试间隔(秒)',
    `last_execute_time` DATETIME DEFAULT NULL COMMENT '上次执行时间',
    `next_execute_time` DATETIME DEFAULT NULL COMMENT '下次执行时间',
    `last_result` VARCHAR(20) DEFAULT NULL COMMENT '上次执行结果',
    `alarm_enabled` TINYINT DEFAULT 0 COMMENT '是否启用告警',
    `alarm_receivers` VARCHAR(500) DEFAULT NULL COMMENT '告警接收人',
    `handler_bean` VARCHAR(100) DEFAULT NULL COMMENT '任务处理器Bean名称',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_key` (`task_key`),
    KEY `idx_status` (`status`),
    KEY `idx_next_execute_time` (`next_execute_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务表';

-- 任务执行记录表
CREATE TABLE IF NOT EXISTS `scheduled_task_execution` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `task_key` VARCHAR(64) NOT NULL COMMENT '任务标识',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
    `duration_ms` BIGINT DEFAULT NULL COMMENT '执行耗时(毫秒)',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-运行中, 1-成功, 2-失败, 3-重试中',
    `result` TEXT DEFAULT NULL COMMENT '执行结果',
    `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
    `trace_id` VARCHAR(64) DEFAULT NULL COMMENT '链路追踪ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_start_time` (`start_time`),
    KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行记录表';
