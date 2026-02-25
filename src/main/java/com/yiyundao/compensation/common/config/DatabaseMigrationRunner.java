package com.yiyundao.compensation.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "migration.audit-log.enabled", havingValue = "true")
public class DatabaseMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running DB migrations (audit_log, core tables) if needed...");
        // 1) Ensure core tables exist (idempotent)
        createTableIfMissing("integration_config",
                "CREATE TABLE `integration_config` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `platform_type` varchar(20) NOT NULL COMMENT '平台类型(wechat/dingtalk/feishu/alipay)',\n" +
                "  `config_json` text COMMENT '配置JSON',\n" +
                "  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',\n" +
                "  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',\n" +
                "  `version` int DEFAULT '0' COMMENT '乐观锁版本号',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_platform_type` (`platform_type`),\n" +
                "  KEY `idx_create_time` (`create_time`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方平台集成配置表';");

        createTableIfMissing("org_department",
                "CREATE TABLE `org_department` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `platform_type` varchar(20) NOT NULL COMMENT '平台类型(wechat/dingtalk/feishu)',\n" +
                "  `platform_dept_id` varchar(100) NOT NULL COMMENT '平台部门ID',\n" +
                "  `name` varchar(200) NOT NULL COMMENT '部门名称',\n" +
                "  `parent_platform_dept_id` varchar(100) DEFAULT NULL COMMENT '平台父部门ID',\n" +
                "  `parent_id` bigint DEFAULT NULL COMMENT '本地父部门ID',\n" +
                "  `order_num` int DEFAULT NULL COMMENT '排序',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',\n" +
                "  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',\n" +
                "  `version` int DEFAULT '0' COMMENT '乐观锁版本号',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_platform_dept` (`platform_type`, `platform_dept_id`),\n" +
                "  KEY `idx_parent` (`parent_platform_dept_id`),\n" +
                "  KEY `idx_create_time` (`create_time`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织部门表';");
        createTableIfMissing("sys_config",
                "CREATE TABLE `sys_config` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `config_key` varchar(100) NOT NULL COMMENT '配置键',\n" +
                "  `config_value` text COMMENT '配置值',\n" +
                "  `remark` varchar(500) DEFAULT NULL COMMENT '配置备注',\n" +
                "  `config_type` varchar(20) DEFAULT 'string' COMMENT '配置类型(string,number,boolean,json)',\n" +
                "  `config_desc` varchar(500) DEFAULT NULL COMMENT '配置描述',\n" +
                "  `is_encrypted` tinyint(1) DEFAULT '0' COMMENT '是否加密(0:否,1:是)',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',\n" +
                "  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)',\n" +
                "  `version` int DEFAULT '0' COMMENT '乐观锁版本号',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_config_key` (`config_key`),\n" +
                "  KEY `idx_create_time` (`create_time`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';");

        // Ensure sys_config.remark exists for compatibility with entity mapping
        addColumnIfMissing("sys_config", "remark",
                "ALTER TABLE sys_config ADD COLUMN `remark` VARCHAR(500) DEFAULT NULL COMMENT '配置备注' AFTER `config_value`");

        createTableIfMissing("notification_record",
                "CREATE TABLE `notification_record` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `notification_type` varchar(50) NOT NULL COMMENT '通知类型',\n" +
                "  `channel` varchar(50) NOT NULL COMMENT '通知渠道',\n" +
                "  `recipient_id` varchar(100) NOT NULL COMMENT '接收人ID',\n" +
                "  `recipient_name` varchar(100) DEFAULT NULL COMMENT '接收人姓名',\n" +
                "  `title` varchar(200) DEFAULT NULL COMMENT '通知标题',\n" +
                "  `content` text COMMENT '通知内容',\n" +
                "  `template_id` varchar(100) DEFAULT NULL COMMENT '模板ID',\n" +
                "  `template_params` text COMMENT '模板参数(JSON)',\n" +
                "  `business_type` varchar(50) DEFAULT NULL COMMENT '业务类型',\n" +
                "  `business_key` varchar(100) DEFAULT NULL COMMENT '业务标识',\n" +
                "  `status` varchar(50) NOT NULL DEFAULT 'pending' COMMENT '通知状态',\n" +
                "  `retry_count` int DEFAULT '0' COMMENT '重试次数',\n" +
                "  `max_retry` int DEFAULT '3' COMMENT '最大重试次数',\n" +
                "  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',\n" +
                "  `send_time` datetime DEFAULT NULL COMMENT '实际发送时间',\n" +
                "  `response_code` varchar(50) DEFAULT NULL COMMENT '响应码',\n" +
                "  `response_message` varchar(255) DEFAULT NULL COMMENT '响应消息',\n" +
                "  `error_message` varchar(500) DEFAULT NULL COMMENT '错误信息',\n" +
                "  `priority` int DEFAULT '0' COMMENT '优先级',\n" +
                "  `fallback_channels` varchar(255) DEFAULT NULL COMMENT '失败回退渠道(JSON数组)',\n" +
                "  `is_read` tinyint(1) DEFAULT '0' COMMENT '是否已读',\n" +
                "  `read_time` datetime DEFAULT NULL COMMENT '读取时间',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',\n" +
                "  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',\n" +
                "  `version` int DEFAULT '0' COMMENT '乐观锁版本号',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_status_next_retry` (`status`, `next_retry_time`),\n" +
                "  KEY `idx_business_type_key` (`business_type`, `business_key`),\n" +
                "  KEY `idx_recipient_channel` (`recipient_id`, `channel`),\n" +
                "  KEY `idx_create_time` (`create_time`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知发送记录表';");

        // 2) audit_log incremental columns
        addColumnIfMissing("audit_log", "update_time",
                "ALTER TABLE audit_log ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`");
        addColumnIfMissing("audit_log", "update_by",
                "ALTER TABLE audit_log ADD COLUMN `update_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人' AFTER `create_by`");
        addColumnIfMissing("audit_log", "deleted",
                "ALTER TABLE audit_log ADD COLUMN `deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)' AFTER `update_by`");
        addColumnIfMissing("audit_log", "version",
                "ALTER TABLE audit_log ADD COLUMN `version` INT DEFAULT '0' COMMENT '乐观锁版本号' AFTER `deleted`");

        // Extend employee: employment_type
        addColumnIfMissing("employee", "employment_type",
                "ALTER TABLE employee ADD COLUMN `employment_type` varchar(20) DEFAULT 'full_time' COMMENT '用工类型(full_time/part_time)' AFTER `position`");

        // New table: employee_department (多部门关联)
        createTableIfMissing("employee_department",
                "CREATE TABLE `employee_department` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `employee_id` bigint NOT NULL COMMENT '员工ID',\n" +
                "  `platform_type` varchar(20) DEFAULT NULL COMMENT '平台类型',\n" +
                "  `platform_dept_id` varchar(100) DEFAULT NULL COMMENT '平台部门ID',\n" +
                "  `local_dept_id` bigint DEFAULT NULL COMMENT '本地部门ID',\n" +
                "  `dept_name` varchar(200) NOT NULL COMMENT '部门名称',\n" +
                "  `is_primary` tinyint(1) DEFAULT '0' COMMENT '是否主部门',\n" +
                "  `order_num` int DEFAULT '0' COMMENT '顺序',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',\n" +
                "  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',\n" +
                "  `version` int DEFAULT '0' COMMENT '乐观锁版本号',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_emp` (`employee_id`),\n" +
                "  KEY `idx_platform_dept` (`platform_type`,`platform_dept_id`),\n" +
                "  CONSTRAINT `fk_emp_dept_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工-部门多对多关联';");

        // ========================= Payroll Core Tables (M1) =========================
        // 1) salary_item
        createTableIfMissing("salary_item",
                "CREATE TABLE `salary_item` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `code` varchar(50) NOT NULL COMMENT '项编码',\n" +
                "  `name` varchar(100) NOT NULL COMMENT '项名称',\n" +
                "  `type` varchar(20) NOT NULL COMMENT 'earning/deduction',\n" +
                "  `taxable` tinyint(1) DEFAULT '1' COMMENT '是否计税',\n" +
                "  `show_on_payslip` tinyint(1) DEFAULT '1' COMMENT '工资条显示',\n" +
                "  `order_num` int DEFAULT '0' COMMENT '排序',\n" +
                "  `status` varchar(20) DEFAULT 'enabled' COMMENT '状态',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                "  `create_by` varchar(50) DEFAULT NULL,\n" +
                "  `update_by` varchar(50) DEFAULT NULL,\n" +
                "  `deleted` tinyint(1) DEFAULT '0',\n" +
                "  `version` int DEFAULT '0',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_item_code` (`code`),\n" +
                "  KEY `idx_status_order` (`status`,`order_num`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资项字典';");

        // 2) salary_template
        createTableIfMissing("salary_template",
                "CREATE TABLE `salary_template` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `name` varchar(100) NOT NULL COMMENT '模板名称',\n" +
                "  `type` varchar(20) NOT NULL COMMENT 'full_time/part_time',\n" +
                "  `items_json` json DEFAULT NULL COMMENT '项配置JSON',\n" +
                "  `tax_rule_json` json DEFAULT NULL COMMENT '税社保口径JSON',\n" +
                "  `status` varchar(20) DEFAULT 'enabled' COMMENT '状态',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                "  `create_by` varchar(50) DEFAULT NULL,\n" +
                "  `update_by` varchar(50) DEFAULT NULL,\n" +
                "  `deleted` tinyint(1) DEFAULT '0',\n" +
                "  `version` int DEFAULT '0',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_type_status` (`type`,`status`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资模板';");

        // 3) pay_cycle
        createTableIfMissing("pay_cycle",
                "CREATE TABLE `pay_cycle` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `type` varchar(20) NOT NULL COMMENT 'monthly/custom',\n" +
                "  `period_label` varchar(20) NOT NULL COMMENT '周期标签(YYYY-MM)',\n" +
                "  `start_date` date DEFAULT NULL,\n" +
                "  `end_date` date DEFAULT NULL,\n" +
                "  `cutoff_date` date DEFAULT NULL,\n" +
                "  `status` varchar(20) DEFAULT 'open',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                "  `create_by` varchar(50) DEFAULT NULL,\n" +
                "  `update_by` varchar(50) DEFAULT NULL,\n" +
                "  `deleted` tinyint(1) DEFAULT '0',\n" +
                "  `version` int DEFAULT '0',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_cycle` (`type`,`period_label`),\n" +
                "  KEY `idx_status_start` (`status`,`start_date`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发薪周期';");

        // 4) payroll_batch
        createTableIfMissing("payroll_batch",
                "CREATE TABLE `payroll_batch` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `pay_cycle_id` bigint DEFAULT NULL COMMENT '周期ID',\n" +
                "  `period_label` varchar(20) DEFAULT NULL COMMENT '周期标签',\n" +
                "  `type` varchar(20) NOT NULL COMMENT 'full_time/part_time',\n" +
                "  `scope_json` json DEFAULT NULL COMMENT '范围JSON',\n" +
                "  `currency` varchar(10) DEFAULT 'CNY',\n" +
                "  `status` varchar(20) DEFAULT 'draft',\n" +
                "  `approval_workflow_id` bigint DEFAULT NULL,\n" +
                "  `payment_batch_no` varchar(50) DEFAULT NULL,\n" +
                "  `remark` varchar(500) DEFAULT NULL,\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                "  `create_by` varchar(50) DEFAULT NULL,\n" +
                "  `update_by` varchar(50) DEFAULT NULL,\n" +
                "  `deleted` tinyint(1) DEFAULT '0',\n" +
                "  `version` int DEFAULT '0',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_batch_type_period_status` (`type`,`period_label`,`status`),\n" +
                "  KEY `idx_cycle` (`pay_cycle_id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发薪批次';");

        // 5) payroll_line
        createTableIfMissing("payroll_line",
                "CREATE TABLE `payroll_line` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `batch_id` bigint NOT NULL COMMENT '批次ID',\n" +
                "  `employee_id` bigint NOT NULL COMMENT '员工ID',\n" +
                "  `employment_type` varchar(20) NOT NULL COMMENT 'full_time/part_time',\n" +
                "  `template_id` bigint DEFAULT NULL COMMENT '模板ID',\n" +
                "  `items_snapshot_json` json DEFAULT NULL COMMENT '项快照JSON',\n" +
                "  `gross_amount` decimal(12,2) DEFAULT '0.00',\n" +
                "  `tax_amount` decimal(12,2) DEFAULT '0.00',\n" +
                "  `social_amount` decimal(12,2) DEFAULT '0.00',\n" +
                "  `net_amount` decimal(12,2) DEFAULT '0.00',\n" +
                "  `currency` varchar(10) DEFAULT 'CNY',\n" +
                "  `status` varchar(20) DEFAULT 'draft',\n" +
                "  `note` varchar(500) DEFAULT NULL,\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                "  `create_by` varchar(50) DEFAULT NULL,\n" +
                "  `update_by` varchar(50) DEFAULT NULL,\n" +
                "  `deleted` tinyint(1) DEFAULT '0',\n" +
                "  `version` int DEFAULT '0',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_batch_employee` (`batch_id`,`employee_id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工资行';");

        // 6) payroll_adjustment
        createTableIfMissing("payroll_adjustment",
                "CREATE TABLE `payroll_adjustment` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `line_id` bigint NOT NULL COMMENT '工资行ID',\n" +
                "  `item_code` varchar(50) NOT NULL COMMENT '项编码',\n" +
                "  `amount` decimal(12,2) NOT NULL COMMENT '调整金额',\n" +
                "  `reason` varchar(200) DEFAULT NULL COMMENT '原因',\n" +
                "  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                "  `create_by` varchar(50) DEFAULT NULL,\n" +
                "  `update_by` varchar(50) DEFAULT NULL,\n" +
                "  `deleted` tinyint(1) DEFAULT '0',\n" +
                "  `version` int DEFAULT '0',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_line` (`line_id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工资调整';");

        // 7) timesheet_entry (optional for PT)
        createTableIfMissing("timesheet_entry",
                "CREATE TABLE `timesheet_entry` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `employee_id` bigint NOT NULL COMMENT '员工ID',\n" +
                "  `work_date` date NOT NULL COMMENT '工作日期',\n" +
                "  `hours` decimal(6,2) DEFAULT NULL COMMENT '工时(小时)',\n" +
                "  `units` decimal(10,2) DEFAULT NULL COMMENT '产出数量',\n" +
                "  `project` varchar(100) DEFAULT NULL COMMENT '项目',\n" +
                "  `department` varchar(100) DEFAULT NULL COMMENT '部门展示',\n" +
                "  `source` varchar(20) DEFAULT 'api' COMMENT 'manual/import/api',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                "  `create_by` varchar(50) DEFAULT NULL,\n" +
                "  `update_by` varchar(50) DEFAULT NULL,\n" +
                "  `deleted` tinyint(1) DEFAULT '0',\n" +
                "  `version` int DEFAULT '0',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_emp_date` (`employee_id`,`work_date`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工时/产出条目';");

        // 8) payroll_import_item (staging for FT imports)
        createTableIfMissing("payroll_import_item",
                "CREATE TABLE `payroll_import_item` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `batch_id` bigint NOT NULL COMMENT '批次ID',\n" +
                "  `employee_id` bigint NOT NULL COMMENT '员工ID',\n" +
                "  `item_code` varchar(50) NOT NULL COMMENT '项编码',\n" +
                "  `amount` decimal(12,2) NOT NULL COMMENT '金额',\n" +
                "  `note` varchar(200) DEFAULT NULL COMMENT '备注',\n" +
                "  `source_name` varchar(200) DEFAULT NULL COMMENT '来源文件',\n" +
                "  `row_no` int DEFAULT NULL COMMENT '行号',\n" +
                "  `status` varchar(20) DEFAULT 'valid' COMMENT 'valid/invalid',\n" +
                "  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误信息',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                "  `create_by` varchar(50) DEFAULT NULL,\n" +
                "  `update_by` varchar(50) DEFAULT NULL,\n" +
                "  `deleted` tinyint(1) DEFAULT '0',\n" +
                "  `version` int DEFAULT '0',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_batch_emp` (`batch_id`,`employee_id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪酬导入暂存';");
        log.info("DB migrations finished.");
    }

    private void addColumnIfMissing(String table, String column, String ddl) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, table, column);
            if (cnt == null || cnt == 0) {
                log.info("Adding column {}.{} via: {}", table, column, ddl);
                jdbcTemplate.execute(ddl);
            } else {
                log.debug("Column {}.{} already exists", table, column);
            }
        } catch (Exception e) {
            log.warn("Failed adding column {}.{}: {}", table, column, e.getMessage());
        }
    }

    private void createTableIfMissing(String table, String ddl) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    Integer.class, table);
            if (cnt == null || cnt == 0) {
                log.info("Creating table {} via DDL", table);
                jdbcTemplate.execute(ddl);
            } else {
                log.debug("Table {} already exists", table);
            }
        } catch (Exception e) {
            log.warn("Failed creating table {}: {}", table, e.getMessage());
        }
    }
}
