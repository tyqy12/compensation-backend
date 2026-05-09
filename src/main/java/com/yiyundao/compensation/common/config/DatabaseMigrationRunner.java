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
        log.info("Running idempotent DB migrations only (audit_log, core tables); destructive bootstrap scripts are excluded.");
        // 1) Ensure core tables exist (idempotent)
        createTableIfMissing("integration_config",
                "CREATE TABLE `integration_config` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `platform_type` varchar(20) NOT NULL COMMENT '平台类型(wechat/dingtalk/feishu/alipay/yunzhanghu)',\n" +
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

        createTableIfMissing("external_identity",
                "CREATE TABLE `external_identity` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `provider` varchar(20) NOT NULL COMMENT '平台标识(wechat/dingtalk/feishu)',\n" +
                "  `tenant_key` varchar(100) NOT NULL DEFAULT 'default' COMMENT '租户标识(如corpId/appId/appKey)',\n" +
                "  `subject_type` varchar(30) NOT NULL DEFAULT 'platform_user_id' COMMENT '主体类型(user_id/open_id/union_id/platform_user_id)',\n" +
                "  `subject_id` varchar(191) NOT NULL COMMENT '平台主体ID',\n" +
                "  `employee_id` bigint DEFAULT NULL COMMENT '关联员工ID',\n" +
                "  `user_id` bigint DEFAULT NULL COMMENT '关联用户ID',\n" +
                "  `is_primary` tinyint(1) NOT NULL DEFAULT '1' COMMENT '同平台主账号标记',\n" +
                "  `status` varchar(20) NOT NULL DEFAULT 'active' COMMENT 'active/inactive',\n" +
                "  `source` varchar(20) DEFAULT NULL COMMENT '来源(sync/manual/oauth/migration/approval)',\n" +
                "  `bound_at` datetime DEFAULT NULL COMMENT '绑定时间',\n" +
                "  `unbound_at` datetime DEFAULT NULL COMMENT '解绑时间',\n" +
                "  `last_seen_at` datetime DEFAULT NULL COMMENT '最近使用时间',\n" +
                "  `ext_json` json DEFAULT NULL COMMENT '扩展字段',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',\n" +
                "  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',\n" +
                "  `version` int DEFAULT '0' COMMENT '乐观锁版本号',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_provider_subject` (`provider`,`tenant_key`,`subject_type`,`subject_id`,`deleted`),\n" +
                "  KEY `idx_employee_provider_status` (`employee_id`,`provider`,`status`),\n" +
                "  KEY `idx_user_provider_status` (`user_id`,`provider`,`status`),\n" +
                "  KEY `idx_subject_lookup` (`provider`,`tenant_key`,`subject_type`,`status`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部平台身份绑定表';");
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
        addColumnIfMissing("employee", "settlement_account_type",
                "ALTER TABLE employee ADD COLUMN `settlement_account_type` varchar(20) DEFAULT NULL COMMENT '收款账户类型(bank_card/alipay/wechat/other)' AFTER `status`");
        addColumnIfMissing("employee", "settlement_account",
                "ALTER TABLE employee ADD COLUMN `settlement_account` varchar(128) DEFAULT NULL COMMENT '收款账户(加密存储)' AFTER `settlement_account_type`");
        addColumnIfMissing("employee", "settlement_account_name",
                "ALTER TABLE employee ADD COLUMN `settlement_account_name` varchar(100) DEFAULT NULL COMMENT '收款账户实名/户名' AFTER `settlement_account`");
        addColumnIfMissing("employee", "bank_branch_name",
                "ALTER TABLE employee ADD COLUMN `bank_branch_name` varchar(120) DEFAULT NULL COMMENT '开户支行' AFTER `bank_name`");
        executeSqlSafely("UPDATE employee SET settlement_account = bank_account " +
                "WHERE (settlement_account IS NULL OR settlement_account = '') " +
                "AND bank_account IS NOT NULL AND bank_account <> ''");
        executeSqlSafely("UPDATE employee SET settlement_account_type = 'bank_card' " +
                "WHERE (settlement_account_type IS NULL OR settlement_account_type = '') " +
                "AND ((settlement_account IS NOT NULL AND settlement_account <> '') OR (bank_account IS NOT NULL AND bank_account <> ''))");
        executeSqlSafely("UPDATE employee SET settlement_account_name = name " +
                "WHERE (settlement_account_name IS NULL OR settlement_account_name = '') " +
                "AND ((settlement_account IS NOT NULL AND settlement_account <> '') OR (bank_account IS NOT NULL AND bank_account <> ''))");

        // Ensure payment_record can be queried by latest entity mapping
        migratePaymentRecordProviderColumns();

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
                "  `data_version` bigint DEFAULT '1' COMMENT '模板数据版本号',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                "  `create_by` varchar(50) DEFAULT NULL,\n" +
                "  `update_by` varchar(50) DEFAULT NULL,\n" +
                "  `deleted` tinyint(1) DEFAULT '0',\n" +
                "  `version` int DEFAULT '0',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_type_status` (`type`,`status`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资模板';");
        addColumnIfMissing("salary_template", "data_version",
                "ALTER TABLE salary_template ADD COLUMN `data_version` bigint DEFAULT '1' COMMENT '模板数据版本号' AFTER `status`");

        // 3) pay_cycle
        createTableIfMissing("pay_cycle",
                "CREATE TABLE `pay_cycle` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `type` varchar(20) NOT NULL COMMENT 'monthly/custom',\n" +
                "  `period_label` varchar(20) NOT NULL COMMENT '周期标签(YYYY-MM)',\n" +
                "  `cycle_code` varchar(64) DEFAULT NULL COMMENT '周期编码',\n" +
                "  `cycle_name` varchar(100) DEFAULT NULL COMMENT '周期名称',\n" +
                "  `cycle_type` varchar(20) DEFAULT NULL COMMENT '周期类型(monthly/semi_monthly/weekly/biweekly/custom)',\n" +
                "  `start_date` date DEFAULT NULL,\n" +
                "  `end_date` date DEFAULT NULL,\n" +
                "  `cutoff_date` date DEFAULT NULL,\n" +
                "  `pay_day` tinyint DEFAULT NULL COMMENT '发薪日(1-31)',\n" +
                "  `lead_days` int DEFAULT NULL COMMENT '提前天数',\n" +
                "  `grace_days` int DEFAULT NULL COMMENT '宽限天数',\n" +
                "  `timezone` varchar(50) DEFAULT NULL COMMENT '时区',\n" +
                "  `description` varchar(500) DEFAULT NULL COMMENT '描述',\n" +
                "  `next_execution_time` datetime DEFAULT NULL COMMENT '下次执行时间',\n" +
                "  `last_execution_time` datetime DEFAULT NULL COMMENT '最近执行时间',\n" +
                "  `status` varchar(20) DEFAULT 'draft',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                "  `create_by` varchar(50) DEFAULT NULL,\n" +
                "  `update_by` varchar(50) DEFAULT NULL,\n" +
                "  `deleted` tinyint(1) DEFAULT '0',\n" +
                "  `version` int DEFAULT '0',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_cycle` (`type`,`period_label`),\n" +
                "  UNIQUE KEY `uk_cycle_code` (`cycle_code`),\n" +
                "  KEY `idx_cycle_status_type` (`status`,`cycle_type`),\n" +
                "  KEY `idx_cycle_next_execution` (`next_execution_time`),\n" +
                "  KEY `idx_status_start` (`status`,`start_date`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发薪周期';");

        // pay_cycle 配置扩展字段与索引补齐
        migratePayCycleColumns();

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

        // Payroll confirmation workflow columns
        migratePayrollConfirmationColumns();
        migratePayrollV21AggregateColumns();
        // Approval workflow incremental columns
        migrateApprovalWorkflowColumns();
        log.info("DB migrations finished.");
    }

    private void migratePayCycleColumns() {
        addColumnIfMissing("pay_cycle", "cycle_code",
                "ALTER TABLE pay_cycle ADD COLUMN `cycle_code` varchar(64) DEFAULT NULL COMMENT '周期编码' AFTER `period_label`");
        addColumnIfMissing("pay_cycle", "cycle_name",
                "ALTER TABLE pay_cycle ADD COLUMN `cycle_name` varchar(100) DEFAULT NULL COMMENT '周期名称' AFTER `cycle_code`");
        addColumnIfMissing("pay_cycle", "cycle_type",
                "ALTER TABLE pay_cycle ADD COLUMN `cycle_type` varchar(20) DEFAULT NULL COMMENT '周期类型(monthly/semi_monthly/weekly/biweekly/custom)' AFTER `cycle_name`");
        addColumnIfMissing("pay_cycle", "pay_day",
                "ALTER TABLE pay_cycle ADD COLUMN `pay_day` tinyint DEFAULT NULL COMMENT '发薪日(1-31)' AFTER `cutoff_date`");
        addColumnIfMissing("pay_cycle", "lead_days",
                "ALTER TABLE pay_cycle ADD COLUMN `lead_days` int DEFAULT NULL COMMENT '提前天数' AFTER `pay_day`");
        addColumnIfMissing("pay_cycle", "grace_days",
                "ALTER TABLE pay_cycle ADD COLUMN `grace_days` int DEFAULT NULL COMMENT '宽限天数' AFTER `lead_days`");
        addColumnIfMissing("pay_cycle", "timezone",
                "ALTER TABLE pay_cycle ADD COLUMN `timezone` varchar(50) DEFAULT NULL COMMENT '时区' AFTER `grace_days`");
        addColumnIfMissing("pay_cycle", "description",
                "ALTER TABLE pay_cycle ADD COLUMN `description` varchar(500) DEFAULT NULL COMMENT '描述' AFTER `timezone`");
        addColumnIfMissing("pay_cycle", "next_execution_time",
                "ALTER TABLE pay_cycle ADD COLUMN `next_execution_time` datetime DEFAULT NULL COMMENT '下次执行时间' AFTER `description`");
        addColumnIfMissing("pay_cycle", "last_execution_time",
                "ALTER TABLE pay_cycle ADD COLUMN `last_execution_time` datetime DEFAULT NULL COMMENT '最近执行时间' AFTER `next_execution_time`");

        executeSqlSafely("UPDATE pay_cycle SET status = 'draft' " +
                "WHERE status IS NULL OR status = ''");
        executeSqlSafely("UPDATE pay_cycle SET cycle_name = CONCAT(period_label, ' 发薪周期') " +
                "WHERE cycle_name IS NULL OR cycle_name = ''");
        executeSqlSafely("UPDATE pay_cycle SET cycle_type = type " +
                "WHERE cycle_type IS NULL OR cycle_type = ''");
        executeSqlSafely("UPDATE pay_cycle SET timezone = 'UTC+8' " +
                "WHERE timezone IS NULL OR timezone = ''");
        executeSqlSafely("UPDATE pay_cycle SET cycle_code = UPPER(CONCAT('CYCLE_', REPLACE(type, '-', '_'), '_', REPLACE(period_label, '-', '_'))) " +
                "WHERE cycle_code IS NULL OR cycle_code = ''");

        addIndexIfMissing("pay_cycle", "uk_cycle_code",
                "CREATE UNIQUE INDEX `uk_cycle_code` ON `pay_cycle` (`cycle_code`)");
        addIndexIfMissing("pay_cycle", "idx_cycle_status_type",
                "CREATE INDEX `idx_cycle_status_type` ON `pay_cycle` (`status`, `cycle_type`)");
        addIndexIfMissing("pay_cycle", "idx_cycle_next_execution",
                "CREATE INDEX `idx_cycle_next_execution` ON `pay_cycle` (`next_execution_time`)");
    }

    private void migratePaymentRecordProviderColumns() {
        addColumnIfMissing("payment_record", "provider_code",
                "ALTER TABLE payment_record ADD COLUMN `provider_code` varchar(32) NOT NULL DEFAULT 'alipay' " +
                        "COMMENT '渠道编码: alipay/yunzhanghu/wechat/bank' AFTER `alipay_trade_no`");
        addColumnIfMissing("payment_record", "provider_order_no",
                "ALTER TABLE payment_record ADD COLUMN `provider_order_no` varchar(64) DEFAULT NULL " +
                        "COMMENT '渠道侧商户订单号(我方生成)' AFTER `provider_code`");
        addColumnIfMissing("payment_record", "provider_trade_no",
                "ALTER TABLE payment_record ADD COLUMN `provider_trade_no` varchar(64) DEFAULT NULL " +
                        "COMMENT '渠道侧平台流水号(渠道返回)' AFTER `provider_order_no`");
        addColumnIfMissing("payment_record", "provider_metadata",
                "ALTER TABLE payment_record ADD COLUMN `provider_metadata` json DEFAULT NULL " +
                        "COMMENT '渠道扩展信息' AFTER `provider_trade_no`");
        addColumnIfMissing("payment_record", "id_card_hash",
                "ALTER TABLE payment_record ADD COLUMN `id_card_hash` varchar(64) DEFAULT NULL " +
                        "COMMENT '收款人身份证哈希' AFTER `provider_metadata`");

        addIndexIfMissing("payment_record", "idx_provider_order",
                "CREATE INDEX `idx_provider_order` ON `payment_record` (`provider_code`, `provider_order_no`)");
        addIndexIfMissing("payment_record", "idx_provider_trade",
                "CREATE INDEX `idx_provider_trade` ON `payment_record` (`provider_code`, `provider_trade_no`)");

        executeSqlSafely("UPDATE payment_record SET provider_code = 'alipay' " +
                "WHERE provider_code IS NULL OR provider_code = ''");
        executeSqlSafely("UPDATE payment_record SET provider_order_no = alipay_order_no " +
                "WHERE (provider_order_no IS NULL OR provider_order_no = '') " +
                "AND alipay_order_no IS NOT NULL AND alipay_order_no <> ''");
        executeSqlSafely("UPDATE payment_record SET provider_trade_no = alipay_trade_no " +
                "WHERE (provider_trade_no IS NULL OR provider_trade_no = '') " +
                "AND alipay_trade_no IS NOT NULL AND alipay_trade_no <> ''");
        executeSqlSafely("UPDATE payment_record SET provider_metadata = '{\"legacy\":true}' WHERE provider_metadata IS NULL");
    }

    private void migratePayrollConfirmationColumns() {
        addColumnIfMissing("payroll_batch", "confirmation_required",
                "ALTER TABLE payroll_batch ADD COLUMN `confirmation_required` tinyint(1) DEFAULT 1 COMMENT '是否需要员工确认' AFTER `payment_batch_no`");
        addColumnIfMissing("payroll_batch", "confirmation_mode",
                "ALTER TABLE payroll_batch ADD COLUMN `confirmation_mode` varchar(20) DEFAULT 'individual' COMMENT '确认模式(individual/group)' AFTER `confirmation_required`");
        addColumnIfMissing("payroll_batch", "confirmation_completed_time",
                "ALTER TABLE payroll_batch ADD COLUMN `confirmation_completed_time` datetime DEFAULT NULL COMMENT '确认完成时间' AFTER `confirmation_mode`");

        // warning 字段在独立迁移脚本中新增；Runner 兜底确保字段存在
        addColumnIfMissing("payroll_line", "warning",
                "ALTER TABLE payroll_line ADD COLUMN `warning` varchar(500) DEFAULT NULL COMMENT '预警信息' AFTER `note`");
        addColumnIfMissing("payroll_line", "confirmation_assignee_employee_id",
                "ALTER TABLE payroll_line ADD COLUMN `confirmation_assignee_employee_id` bigint DEFAULT NULL COMMENT '确认负责人员工ID' AFTER `warning`");
        addColumnIfMissing("payroll_line", "confirmation_status",
                "ALTER TABLE payroll_line ADD COLUMN `confirmation_status` varchar(30) DEFAULT 'pending' COMMENT '确认状态' AFTER `confirmation_assignee_employee_id`");
        addColumnIfMissing("payroll_line", "confirmed_by_user_id",
                "ALTER TABLE payroll_line ADD COLUMN `confirmed_by_user_id` bigint DEFAULT NULL COMMENT '确认人用户ID' AFTER `confirmation_status`");
        addColumnIfMissing("payroll_line", "confirmed_by_employee_id",
                "ALTER TABLE payroll_line ADD COLUMN `confirmed_by_employee_id` bigint DEFAULT NULL COMMENT '确认人员工ID' AFTER `confirmed_by_user_id`");
        addColumnIfMissing("payroll_line", "confirmed_at",
                "ALTER TABLE payroll_line ADD COLUMN `confirmed_at` datetime DEFAULT NULL COMMENT '确认时间' AFTER `confirmed_by_employee_id`");
        addColumnIfMissing("payroll_line", "confirmation_comment",
                "ALTER TABLE payroll_line ADD COLUMN `confirmation_comment` varchar(500) DEFAULT NULL COMMENT '确认备注/签字' AFTER `confirmed_at`");
        addColumnIfMissing("payroll_line", "objection_reason",
                "ALTER TABLE payroll_line ADD COLUMN `objection_reason` varchar(500) DEFAULT NULL COMMENT '异议原因' AFTER `confirmation_comment`");
        addColumnIfMissing("payroll_line", "objection_at",
                "ALTER TABLE payroll_line ADD COLUMN `objection_at` datetime DEFAULT NULL COMMENT '异议时间' AFTER `objection_reason`");
        addColumnIfMissing("payroll_line", "dispute_workflow_id",
                "ALTER TABLE payroll_line ADD COLUMN `dispute_workflow_id` bigint DEFAULT NULL COMMENT '异议审批流程ID' AFTER `objection_at`");

        addIndexIfMissing("payroll_line", "idx_confirmation_assignee_status",
                "CREATE INDEX `idx_confirmation_assignee_status` ON `payroll_line` (`confirmation_assignee_employee_id`, `confirmation_status`)");
        addIndexIfMissing("payroll_line", "idx_dispute_workflow",
                "CREATE INDEX `idx_dispute_workflow` ON `payroll_line` (`dispute_workflow_id`)");

        executeSqlSafely("UPDATE payroll_batch SET confirmation_required = 1 WHERE confirmation_required IS NULL");
        executeSqlSafely("UPDATE payroll_batch SET confirmation_mode = 'individual' " +
                "WHERE confirmation_mode IS NULL OR confirmation_mode = ''");
        executeSqlSafely("UPDATE payroll_line SET confirmation_assignee_employee_id = employee_id " +
                "WHERE confirmation_assignee_employee_id IS NULL");
        executeSqlSafely("UPDATE payroll_line SET confirmation_status = 'pending' " +
                "WHERE confirmation_status IS NULL OR confirmation_status = ''");

        executeSqlSafely("INSERT INTO sys_config(config_key, config_value, config_type, config_desc, create_time, update_time) " +
                "SELECT 'payroll.dispute.approval.flow', '', 'json', '薪酬异议审批流程配置(JSON，空值使用默认链路)', NOW(), NOW() " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='payroll.dispute.approval.flow')");
    }

    private void migratePayrollV21AggregateColumns() {
        addColumnIfMissing("payroll_batch", "calculation_status",
                "ALTER TABLE payroll_batch ADD COLUMN `calculation_status` varchar(32) DEFAULT 'draft' COMMENT '核算状态' AFTER `currency`");
        addColumnIfMissing("payroll_batch", "batch_revision",
                "ALTER TABLE payroll_batch ADD COLUMN `batch_revision` int DEFAULT 1 COMMENT '业务批次版本号' AFTER `calculation_status`");
        addIndexIfMissing("payroll_batch", "idx_calculation_status",
                "CREATE INDEX `idx_calculation_status` ON `payroll_batch` (`calculation_status`)");
        addIndexIfMissing("payroll_batch", "idx_batch_revision",
                "CREATE INDEX `idx_batch_revision` ON `payroll_batch` (`batch_revision`)");
        executeSqlSafely("UPDATE payroll_batch SET calculation_status = CASE " +
                "WHEN status IN ('draft', 'locked') THEN status " +
                "WHEN status = 'calculating' THEN 'calculating' " +
                "WHEN status IN ('confirming', 'dispute_processing', 'confirmed', 'submitted', 'approved', 'pay_processing', 'paid', 'archived') THEN 'calculated' " +
                "WHEN status IN ('rejected', 'pay_failed', 'failed') THEN 'failed' " +
                "ELSE COALESCE(NULLIF(calculation_status, ''), 'draft') END");
        executeSqlSafely("UPDATE payroll_batch SET batch_revision = 1 WHERE batch_revision IS NULL OR batch_revision < 1");

        createTableIfMissing("payroll_confirmation", """
                CREATE TABLE `payroll_confirmation` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `confirmation_no` varchar(64) NOT NULL COMMENT '确认单号',
                  `batch_id` bigint NOT NULL COMMENT '核算批次ID',
                  `batch_revision` int NOT NULL COMMENT '批次版本号',
                  `require_confirmation` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否需要确认',
                  `deadline` datetime DEFAULT NULL COMMENT '确认截止时间',
                  `timeout_strategy` varchar(32) DEFAULT NULL COMMENT '超时策略',
                  `confirmation_status` varchar(32) NOT NULL COMMENT '确认单状态',
                  `total_employees` int NOT NULL DEFAULT 0 COMMENT '总人数',
                  `confirmed_count` int NOT NULL DEFAULT 0 COMMENT '已确认人数',
                  `rejected_count` int NOT NULL DEFAULT 0 COMMENT '拒绝人数',
                  `policy_id` varchar(64) DEFAULT NULL COMMENT '策略ID/模式',
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
                  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
                  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_confirmation_no` (`confirmation_no`),
                  UNIQUE KEY `uk_confirmation_batch_revision_deleted` (`batch_id`,`batch_revision`,`deleted`),
                  KEY `idx_confirmation_status` (`confirmation_status`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资确认单';
                """);

        createTableIfMissing("payroll_confirmation_record", """
                CREATE TABLE `payroll_confirmation_record` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `confirmation_id` bigint NOT NULL COMMENT '确认单ID',
                  `employee_id` bigint NOT NULL COMMENT '员工ID',
                  `line_id` bigint NOT NULL COMMENT '薪资行ID',
                  `record_status` varchar(32) NOT NULL COMMENT '确认记录状态',
                  `reject_reason` varchar(500) DEFAULT NULL COMMENT '拒绝原因',
                  `comment` varchar(500) DEFAULT NULL COMMENT '备注',
                  `confirmed_at` datetime DEFAULT NULL COMMENT '确认时间',
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
                  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
                  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_confirmation_employee_deleted` (`confirmation_id`,`employee_id`,`deleted`),
                  KEY `idx_confirmation_line` (`confirmation_id`,`line_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资确认记录';
                """);

        createTableIfMissing("payroll_distribution", """
                CREATE TABLE `payroll_distribution` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `distribution_no` varchar(64) NOT NULL COMMENT '发放单号',
                  `batch_id` bigint NOT NULL COMMENT '核算批次ID',
                  `batch_revision` int NOT NULL COMMENT '批次版本号',
                  `total_amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '应发总额快照',
                  `total_count` int NOT NULL DEFAULT 0 COMMENT '应发人数快照',
                  `scheduled_date` date NOT NULL COMMENT '计划发放日期',
                  `retry_limit` int NOT NULL DEFAULT 3 COMMENT '最大重试次数',
                  `allow_partial` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否允许部分成功',
                  `distribution_status` varchar(32) NOT NULL COMMENT '发放状态',
                  `actual_amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '实发金额',
                  `success_count` int NOT NULL DEFAULT 0 COMMENT '成功人数',
                  `failed_count` int NOT NULL DEFAULT 0 COMMENT '失败人数',
                  `current_attempt` int NOT NULL DEFAULT 0 COMMENT '当前尝试号',
                  `approval_workflow_id` bigint DEFAULT NULL COMMENT '审批流ID',
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
                  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
                  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_distribution_no` (`distribution_no`),
                  UNIQUE KEY `uk_distribution_batch_revision_deleted` (`batch_id`,`batch_revision`,`deleted`),
                  KEY `idx_distribution_status` (`distribution_status`),
                  KEY `idx_distribution_schedule` (`scheduled_date`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资发放单';
                """);

        createTableIfMissing("payroll_distribution_item", """
                CREATE TABLE `payroll_distribution_item` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `distribution_id` bigint NOT NULL COMMENT '发放单ID',
                  `employee_id` bigint NOT NULL COMMENT '员工ID',
                  `line_id` bigint NOT NULL COMMENT '薪资行ID',
                  `employee_name` varchar(128) DEFAULT NULL COMMENT '员工姓名快照',
                  `recipient_name` varchar(128) DEFAULT NULL COMMENT '收款人姓名快照',
                  `account_no_encrypted` text COMMENT '收款账户密文',
                  `account_no_masked` varchar(128) DEFAULT NULL COMMENT '收款账户脱敏',
                  `account_type` varchar(32) DEFAULT NULL COMMENT '账户类型',
                  `payment_method` varchar(32) DEFAULT NULL COMMENT '支付方式',
                  `provider_code` varchar(32) DEFAULT NULL COMMENT '结算渠道编码',
                  `amount` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '应发金额快照',
                  `item_status` varchar(32) NOT NULL COMMENT '明细状态',
                  `payment_record_id` bigint DEFAULT NULL COMMENT '最新支付记录ID',
                  `failure_reason` varchar(500) DEFAULT NULL COMMENT '失败原因',
                  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
                  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
                  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_distribution_employee_deleted` (`distribution_id`,`employee_id`,`deleted`),
                  KEY `idx_distribution_item_status` (`distribution_id`,`item_status`),
                  KEY `idx_distribution_payment_record` (`payment_record_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资发放明细';
                """);

        createTableIfMissing("payroll_approval_projection", """
                CREATE TABLE `payroll_approval_projection` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `batch_id` bigint NOT NULL COMMENT '核算批次ID',
                  `batch_revision` int NOT NULL COMMENT '批次版本号',
                  `distribution_id` bigint DEFAULT NULL COMMENT '发放单ID',
                  `workflow_id` bigint NOT NULL COMMENT '审批流ID',
                  `business_status` varchar(32) NOT NULL COMMENT '业务状态',
                  `submitter_id` bigint DEFAULT NULL COMMENT '提交人ID',
                  `submitted_at` datetime DEFAULT NULL COMMENT '提交时间',
                  `current_approver_id` bigint DEFAULT NULL COMMENT '当前审批人',
                  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
                  `result` varchar(64) DEFAULT NULL COMMENT '审批结果',
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
                  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
                  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_workflow_deleted` (`workflow_id`,`deleted`),
                  KEY `idx_projection_distribution` (`distribution_id`),
                  KEY `idx_projection_status` (`business_status`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资审批投影';
                """);

        createTableIfMissing("payroll_reconciliation_task", """
                CREATE TABLE `payroll_reconciliation_task` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `distribution_id` bigint NOT NULL COMMENT '发放单ID',
                  `task_status` varchar(32) NOT NULL COMMENT '任务状态',
                  `expected_amount` decimal(15,2) DEFAULT '0.00' COMMENT '应发金额',
                  `actual_amount` decimal(15,2) DEFAULT '0.00' COMMENT '实发金额',
                  `difference` decimal(15,2) DEFAULT '0.00' COMMENT '差异金额',
                  `result` varchar(32) DEFAULT NULL COMMENT '结果',
                  `difference_detail` text COMMENT '差异明细JSON',
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
                  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
                  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_reconciliation_distribution_deleted` (`distribution_id`,`deleted`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资对账任务';
                """);

        addColumnIfMissing("payment_batch", "distribution_id",
                "ALTER TABLE payment_batch ADD COLUMN `distribution_id` bigint DEFAULT NULL COMMENT '关联发放单ID' AFTER `status`");
        addColumnIfMissing("payment_batch", "payment_status",
                "ALTER TABLE payment_batch ADD COLUMN `payment_status` varchar(32) DEFAULT NULL COMMENT '支付处理状态' AFTER `distribution_id`");
        addIndexIfMissing("payment_batch", "idx_distribution",
                "CREATE INDEX `idx_distribution` ON `payment_batch` (`distribution_id`)");
        addIndexIfMissing("payment_batch", "idx_payment_status",
                "CREATE INDEX `idx_payment_status` ON `payment_batch` (`payment_status`)");
        executeSqlSafely("UPDATE payment_batch SET payment_status = CASE " +
                "WHEN status = 'draft' THEN 'created' " +
                "WHEN status IN ('submitted', 'approved') THEN 'submitted' " +
                "WHEN status = 'processing' THEN 'processing' " +
                "WHEN status = 'completed' THEN 'success' " +
                "WHEN status = 'failed' THEN 'failed' " +
                "ELSE COALESCE(NULLIF(payment_status, ''), 'created') END " +
                "WHERE payment_status IS NULL OR payment_status = ''");
    }

    private void migrateApprovalWorkflowColumns() {
        addColumnIfMissing("approval_workflow", "employee_id",
                "ALTER TABLE approval_workflow ADD COLUMN `employee_id` bigint DEFAULT NULL COMMENT '关联员工ID' AFTER `initiator_id`");
        addIndexIfMissing("approval_workflow", "idx_employee",
                "CREATE INDEX `idx_employee` ON `approval_workflow` (`employee_id`)");
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

    private void addIndexIfMissing(String table, String index, String ddl) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() " +
                            "AND TABLE_NAME = ? AND INDEX_NAME = ?",
                    Integer.class, table, index);
            if (cnt == null || cnt == 0) {
                log.info("Adding index {}.{} via: {}", table, index, ddl);
                jdbcTemplate.execute(ddl);
            } else {
                log.debug("Index {}.{} already exists", table, index);
            }
        } catch (Exception e) {
            log.warn("Failed adding index {}.{}: {}", table, index, e.getMessage());
        }
    }

    private void executeSqlSafely(String sql) {
        try {
            int affectedRows = jdbcTemplate.update(sql);
            log.info("Executed data migration SQL, affectedRows={}", affectedRows);
        } catch (Exception e) {
            log.warn("Failed executing data migration SQL [{}]: {}", sql, e.getMessage());
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
