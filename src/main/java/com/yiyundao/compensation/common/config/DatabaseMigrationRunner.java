package com.yiyundao.compensation.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${migration.runner.enabled:false}' == 'true' || '${migration.audit-log.enabled:false}' == 'true'")
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

        migrateRbacSchema();

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
        executeSqlSafely("ALTER TABLE employee MODIFY COLUMN `department` varchar(500) DEFAULT NULL COMMENT '部门(兼容展示字段，多部门关系见employee_department)'");
        executeSqlSafely("ALTER TABLE employee MODIFY COLUMN `settlement_account` text COMMENT '收款账户(加密存储)'");
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
        addIndexIfMissing("employee_department", "idx_employee_department_platform",
                "CREATE INDEX `idx_employee_department_platform` ON `employee_department` (`employee_id`, `platform_type`, `deleted`)");
        executeSqlSafely("UPDATE employee_department SET platform_type = 'manual' "
                + "WHERE platform_type IS NULL OR platform_type = ''");

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
                "  `input_snapshot_json` json DEFAULT NULL COMMENT '薪资输入事实完整快照',\n" +
                "  `rule_snapshot_json` json DEFAULT NULL COMMENT '薪资规则完整快照',\n" +
                "  `status` varchar(20) DEFAULT 'draft',\n" +
                "  `approval_workflow_id` bigint DEFAULT NULL,\n" +
                "  `payment_batch_no` varchar(50) DEFAULT NULL,\n" +
                "  `payment_status` varchar(32) DEFAULT NULL COMMENT '支付子域状态投影',\n" +
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
                "  `batch_revision` int NOT NULL DEFAULT '1' COMMENT '工资行所属批次版本号',\n" +
                "  `employee_id` bigint NOT NULL COMMENT '员工ID',\n" +
                "  `employment_type` varchar(20) NOT NULL COMMENT 'full_time/part_time',\n" +
                "  `template_id` bigint DEFAULT NULL COMMENT '模板ID',\n" +
                "  `items_snapshot_json` json DEFAULT NULL COMMENT '项快照JSON',\n" +
                "  `input_snapshot_hash` varchar(64) DEFAULT NULL COMMENT '薪资输入事实快照摘要',\n" +
                "  `rule_snapshot_hash` varchar(64) DEFAULT NULL COMMENT '薪资规则快照摘要',\n" +
                "  `calculation_engine_version` varchar(64) DEFAULT NULL COMMENT '计算引擎版本',\n" +
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
                "  KEY `idx_batch_employee` (`batch_id`,`employee_id`),\n" +
                "  KEY `idx_batch_revision_employee` (`batch_id`,`batch_revision`,`employee_id`)\n" +
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
        migratePayrollRuleVersionColumns();
        migratePayrollComplianceFoundation();
        migratePayrollIntegrityHardening();
        migratePayrollComplianceResources();
        retireLegacyPayrollContent();
        migrateFrontendRouteResources();
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

    private void migrateRbacSchema() {
        createTableIfMissing("sys_resource",
                "CREATE TABLE `sys_resource` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `type` varchar(20) NOT NULL COMMENT '资源类型: MENU/VIEW/ACTION/API',\n" +
                "  `code` varchar(100) NOT NULL COMMENT '全局唯一编码',\n" +
                "  `name` varchar(100) NOT NULL COMMENT '资源名称',\n" +
                "  `path` varchar(255) DEFAULT NULL COMMENT '路由或接口路径',\n" +
                "  `component` varchar(255) DEFAULT NULL COMMENT '前端组件',\n" +
                "  `icon` varchar(100) DEFAULT NULL COMMENT '图标',\n" +
                "  `parent_id` bigint DEFAULT NULL COMMENT '父资源ID',\n" +
                "  `order_num` int DEFAULT '0' COMMENT '排序号',\n" +
                "  `props_json` json DEFAULT NULL COMMENT '扩展元信息(JSON)',\n" +
                "  `status` varchar(20) DEFAULT 'enabled' COMMENT '状态',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',\n" +
                "  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',\n" +
                "  `version` int DEFAULT '0' COMMENT '乐观锁版本号',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_resource_code` (`code`),\n" +
                "  KEY `idx_sys_resource_type` (`type`),\n" +
                "  KEY `idx_sys_resource_parent_order` (`parent_id`, `order_num`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统资源表';");

        createTableIfMissing("sys_role",
                "CREATE TABLE `sys_role` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `code` varchar(50) NOT NULL COMMENT '角色编码',\n" +
                "  `name` varchar(100) NOT NULL COMMENT '角色名称',\n" +
                "  `description` varchar(255) DEFAULT NULL COMMENT '描述',\n" +
                "  `role_type` varchar(20) DEFAULT 'CUSTOM' COMMENT '角色类型: SYSTEM/BUSINESS/CUSTOM',\n" +
                "  `sort_order` int DEFAULT '0' COMMENT '排序号',\n" +
                "  `is_editable` tinyint(1) DEFAULT '1' COMMENT '是否可编辑',\n" +
                "  `icon` varchar(100) DEFAULT NULL COMMENT '角色图标',\n" +
                "  `remarks` varchar(500) DEFAULT NULL COMMENT '备注',\n" +
                "  `status` varchar(20) DEFAULT 'enabled' COMMENT '状态',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',\n" +
                "  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',\n" +
                "  `version` int DEFAULT '0' COMMENT '乐观锁版本号',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_role_code` (`code`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';");

        createTableIfMissing("sys_role_resource",
                "CREATE TABLE `sys_role_resource` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `role_id` bigint NOT NULL,\n" +
                "  `resource_id` bigint NOT NULL,\n" +
                "  `actions_json` json DEFAULT NULL COMMENT '按钮/动作集合(JSON 数组)',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',\n" +
                "  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',\n" +
                "  `version` int DEFAULT '0' COMMENT '乐观锁版本号',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_role_resource` (`role_id`, `resource_id`),\n" +
                "  KEY `idx_sys_role_resource_role` (`role_id`),\n" +
                "  KEY `idx_sys_role_resource_resource` (`resource_id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-资源授权关系';");

        createTableIfMissing("sys_user_role",
                "CREATE TABLE `sys_user_role` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `user_id` bigint NOT NULL,\n" +
                "  `role_id` bigint NOT NULL,\n" +
                "  `granted_by` bigint DEFAULT NULL COMMENT '授权人ID',\n" +
                "  `granted_at` datetime DEFAULT NULL COMMENT '授权时间',\n" +
                "  `expires_at` datetime DEFAULT NULL COMMENT '过期时间',\n" +
                "  `remarks` varchar(500) DEFAULT NULL COMMENT '备注',\n" +
                "  `delete_by` varchar(50) DEFAULT NULL COMMENT '删除人',\n" +
                "  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',\n" +
                "  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',\n" +
                "  `version` int DEFAULT '0' COMMENT '乐观锁版本号',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),\n" +
                "  KEY `idx_sys_user_role_role` (`role_id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关系';");

        createTableIfMissing("sys_user_resource",
                "CREATE TABLE `sys_user_resource` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `user_id` bigint NOT NULL,\n" +
                "  `resource_id` bigint NOT NULL,\n" +
                "  `actions_json` json DEFAULT NULL COMMENT '按钮/动作集合(JSON 数组)',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',\n" +
                "  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',\n" +
                "  `version` int DEFAULT '0' COMMENT '乐观锁版本号',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_user_resource` (`user_id`, `resource_id`),\n" +
                "  KEY `idx_sys_user_resource_user` (`user_id`),\n" +
                "  KEY `idx_sys_user_resource_resource` (`resource_id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-资源个性授权';");

        createTableIfMissing("resource_snapshot",
                "CREATE TABLE `resource_snapshot` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `workflow_id` bigint NOT NULL COMMENT '审批流程ID',\n" +
                "  `before_json` json DEFAULT NULL COMMENT '变更前快照',\n" +
                "  `after_json` json DEFAULT NULL COMMENT '变更后拟态',\n" +
                "  `actor_id` bigint DEFAULT NULL COMMENT '发起人',\n" +
                "  `reason` varchar(255) DEFAULT NULL COMMENT '原因',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  UNIQUE KEY `uk_snapshot_workflow` (`workflow_id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源授权变更快照';");

        addColumnIfMissing("sys_user", "permission_version",
                "ALTER TABLE sys_user ADD COLUMN `permission_version` int DEFAULT '0' COMMENT '权限版本'");

        addBaseEntityColumnsIfMissing("sys_resource");
        addBaseEntityColumnsIfMissing("sys_role");
        addBaseEntityColumnsIfMissing("sys_role_resource");
        addBaseEntityColumnsIfMissing("sys_user_resource");

        addColumnIfMissing("sys_role", "role_type",
                "ALTER TABLE sys_role ADD COLUMN `role_type` varchar(20) DEFAULT 'CUSTOM' COMMENT '角色类型: SYSTEM/BUSINESS/CUSTOM'");
        addColumnIfMissing("sys_role", "sort_order",
                "ALTER TABLE sys_role ADD COLUMN `sort_order` int DEFAULT '0' COMMENT '排序号'");
        addColumnIfMissing("sys_role", "is_editable",
                "ALTER TABLE sys_role ADD COLUMN `is_editable` tinyint(1) DEFAULT '1' COMMENT '是否可编辑'");
        addColumnIfMissing("sys_role", "icon",
                "ALTER TABLE sys_role ADD COLUMN `icon` varchar(100) DEFAULT NULL COMMENT '角色图标'");
        addColumnIfMissing("sys_role", "remarks",
                "ALTER TABLE sys_role ADD COLUMN `remarks` varchar(500) DEFAULT NULL COMMENT '备注'");

        addColumnIfMissing("sys_user_role", "id",
                "ALTER TABLE sys_user_role ADD COLUMN `id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID'");
        addColumnIfMissing("sys_user_role", "granted_by",
                "ALTER TABLE sys_user_role ADD COLUMN `granted_by` bigint DEFAULT NULL COMMENT '授权人ID'");
        addColumnIfMissing("sys_user_role", "granted_at",
                "ALTER TABLE sys_user_role ADD COLUMN `granted_at` datetime DEFAULT NULL COMMENT '授权时间'");
        addColumnIfMissing("sys_user_role", "expires_at",
                "ALTER TABLE sys_user_role ADD COLUMN `expires_at` datetime DEFAULT NULL COMMENT '过期时间'");
        addColumnIfMissing("sys_user_role", "remarks",
                "ALTER TABLE sys_user_role ADD COLUMN `remarks` varchar(500) DEFAULT NULL COMMENT '备注'");
        addColumnIfMissing("sys_user_role", "delete_by",
                "ALTER TABLE sys_user_role ADD COLUMN `delete_by` varchar(50) DEFAULT NULL COMMENT '删除人'");
        addColumnIfMissing("sys_user_role", "delete_time",
                "ALTER TABLE sys_user_role ADD COLUMN `delete_time` datetime DEFAULT NULL COMMENT '删除时间'");
        addBaseEntityColumnsIfMissing("sys_user_role");

        addIndexIfMissing("sys_resource", "uk_resource_code",
                "CREATE UNIQUE INDEX `uk_resource_code` ON `sys_resource` (`code`)");
        addIndexIfMissing("sys_role", "uk_role_code",
                "CREATE UNIQUE INDEX `uk_role_code` ON `sys_role` (`code`)");
        addIndexIfMissing("sys_role_resource", "uk_role_resource",
                "CREATE UNIQUE INDEX `uk_role_resource` ON `sys_role_resource` (`role_id`, `resource_id`)");
        addIndexIfMissing("sys_user_resource", "uk_user_resource",
                "CREATE UNIQUE INDEX `uk_user_resource` ON `sys_user_resource` (`user_id`, `resource_id`)");
        addIndexIfMissing("sys_user_role", "uk_user_role",
                "CREATE UNIQUE INDEX `uk_user_role` ON `sys_user_role` (`user_id`, `role_id`)");
        addIndexIfMissing("sys_user_role", "idx_sys_user_role_role",
                "CREATE INDEX `idx_sys_user_role_role` ON `sys_user_role` (`role_id`)");

        executeSqlSafely("UPDATE sys_user SET permission_version = 0 WHERE permission_version IS NULL");
        executeSqlSafely("UPDATE sys_resource SET deleted = 0 WHERE deleted IS NULL");
        executeSqlSafely("UPDATE sys_role SET deleted = 0 WHERE deleted IS NULL");
        executeSqlSafely("UPDATE sys_role_resource SET deleted = 0 WHERE deleted IS NULL");
        executeSqlSafely("UPDATE sys_user_role SET deleted = 0 WHERE deleted IS NULL");
        executeSqlSafely("UPDATE sys_user_resource SET deleted = 0 WHERE deleted IS NULL");

        seedRbacRolesAndUserAssignments();
    }

    private void addBaseEntityColumnsIfMissing(String table) {
        String quotedTable = "`" + table + "`";
        addColumnIfMissing(table, "create_time",
                "ALTER TABLE " + quotedTable + " ADD COLUMN `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'");
        addColumnIfMissing(table, "update_time",
                "ALTER TABLE " + quotedTable + " ADD COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'");
        addColumnIfMissing(table, "create_by",
                "ALTER TABLE " + quotedTable + " ADD COLUMN `create_by` varchar(50) DEFAULT NULL COMMENT '创建人'");
        addColumnIfMissing(table, "update_by",
                "ALTER TABLE " + quotedTable + " ADD COLUMN `update_by` varchar(50) DEFAULT NULL COMMENT '更新人'");
        addColumnIfMissing(table, "deleted",
                "ALTER TABLE " + quotedTable + " ADD COLUMN `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除'");
        addColumnIfMissing(table, "version",
                "ALTER TABLE " + quotedTable + " ADD COLUMN `version` int DEFAULT '0' COMMENT '乐观锁版本号'");
    }

    private void seedRbacRolesAndUserAssignments() {
        executeSqlSafely("""
                INSERT INTO sys_role (code, name, description, role_type, sort_order, is_editable, icon, status, create_by)
                SELECT 'ADMIN', '系统管理员', '拥有所有系统权限', 'SYSTEM', 1, 0, 'crown', 'enabled', 'system'
                WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'ADMIN')
                """);
        executeSqlSafely("""
                INSERT INTO sys_role (code, name, description, role_type, sort_order, is_editable, icon, status, create_by)
                SELECT 'MANAGER', '部门经理', '部门管理和审批权限', 'BUSINESS', 2, 1, 'team', 'enabled', 'system'
                WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'MANAGER')
                """);
        executeSqlSafely("""
                INSERT INTO sys_role (code, name, description, role_type, sort_order, is_editable, icon, status, create_by)
                SELECT 'FINANCE', '财务人员', '薪酬管理和支付权限', 'BUSINESS', 3, 1, 'wallet', 'enabled', 'system'
                WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'FINANCE')
                """);
        executeSqlSafely("""
                INSERT INTO sys_role (code, name, description, role_type, sort_order, is_editable, icon, status, create_by)
                SELECT 'HR', '人力资源', '员工管理权限', 'BUSINESS', 4, 1, 'user', 'enabled', 'system'
                WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'HR')
                """);
        executeSqlSafely("""
                INSERT INTO sys_role (code, name, description, role_type, sort_order, is_editable, icon, status, create_by)
                SELECT 'EMPLOYEE', '普通员工', '个人工资条查看权限', 'BUSINESS', 5, 1, 'contacts', 'enabled', 'system'
                WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'EMPLOYEE')
                """);

        executeSqlSafely("""
                INSERT INTO sys_user_role (user_id, role_id, granted_by, granted_at, deleted, create_time, create_by)
                SELECT u.id, r.id, 1, NOW(), 0, NOW(), 'system_migration'
                FROM sys_user u
                JOIN sys_role r ON r.code = 'ADMIN' AND r.status = 'enabled'
                WHERE u.roles IS NOT NULL
                  AND (
                      REPLACE(CONCAT(',', u.roles, ','), ' ', '') LIKE '%,ROLE_ADMIN,%'
                      OR REPLACE(CONCAT(',', u.roles, ','), ' ', '') LIKE '%,ADMIN,%'
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM sys_user_role ur
                      WHERE ur.user_id = u.id AND ur.role_id = r.id AND ur.deleted = 0
                  )
                """);
        executeSqlSafely("""
                INSERT INTO sys_user_role (user_id, role_id, granted_by, granted_at, deleted, create_time, create_by)
                SELECT u.id, r.id, 1, NOW(), 0, NOW(), 'system_migration'
                FROM sys_user u
                JOIN sys_role r ON r.code = 'MANAGER' AND r.status = 'enabled'
                WHERE u.roles IS NOT NULL
                  AND (
                      REPLACE(CONCAT(',', u.roles, ','), ' ', '') LIKE '%,ROLE_MANAGER,%'
                      OR REPLACE(CONCAT(',', u.roles, ','), ' ', '') LIKE '%,MANAGER,%'
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM sys_user_role ur
                      WHERE ur.user_id = u.id AND ur.role_id = r.id AND ur.deleted = 0
                  )
                """);
        executeSqlSafely("""
                INSERT INTO sys_user_role (user_id, role_id, granted_by, granted_at, deleted, create_time, create_by)
                SELECT u.id, r.id, 1, NOW(), 0, NOW(), 'system_migration'
                FROM sys_user u
                JOIN sys_role r ON r.code = 'FINANCE' AND r.status = 'enabled'
                WHERE u.roles IS NOT NULL
                  AND (
                      REPLACE(CONCAT(',', u.roles, ','), ' ', '') LIKE '%,ROLE_FINANCE,%'
                      OR REPLACE(CONCAT(',', u.roles, ','), ' ', '') LIKE '%,FINANCE,%'
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM sys_user_role ur
                      WHERE ur.user_id = u.id AND ur.role_id = r.id AND ur.deleted = 0
                  )
                """);
        executeSqlSafely("""
                INSERT INTO sys_user_role (user_id, role_id, granted_by, granted_at, deleted, create_time, create_by)
                SELECT u.id, r.id, 1, NOW(), 0, NOW(), 'system_migration'
                FROM sys_user u
                JOIN sys_role r ON r.code = 'HR' AND r.status = 'enabled'
                WHERE u.roles IS NOT NULL
                  AND (
                      REPLACE(CONCAT(',', u.roles, ','), ' ', '') LIKE '%,ROLE_HR,%'
                      OR REPLACE(CONCAT(',', u.roles, ','), ' ', '') LIKE '%,HR,%'
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM sys_user_role ur
                      WHERE ur.user_id = u.id AND ur.role_id = r.id AND ur.deleted = 0
                  )
                """);
        executeSqlSafely("""
                INSERT INTO sys_user_role (user_id, role_id, granted_by, granted_at, deleted, create_time, create_by)
                SELECT u.id, r.id, 1, NOW(), 0, NOW(), 'system_migration'
                FROM sys_user u
                JOIN sys_role r ON r.code = 'EMPLOYEE' AND r.status = 'enabled'
                WHERE u.roles IS NOT NULL
                  AND (
                      REPLACE(CONCAT(',', u.roles, ','), ' ', '') LIKE '%,ROLE_EMPLOYEE,%'
                      OR REPLACE(CONCAT(',', u.roles, ','), ' ', '') LIKE '%,EMPLOYEE,%'
                      OR REPLACE(CONCAT(',', u.roles, ','), ' ', '') LIKE '%,ROLE_USER,%'
                      OR REPLACE(CONCAT(',', u.roles, ','), ' ', '') LIKE '%,USER,%'
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM sys_user_role ur
                      WHERE ur.user_id = u.id AND ur.role_id = r.id AND ur.deleted = 0
                  )
                """);

        executeSqlSafely("""
                INSERT INTO sys_role_resource (role_id, resource_id, actions_json, create_time, create_by)
                SELECT r.id, res.id, '["*"]', NOW(), 'system_migration'
                FROM sys_role r
                JOIN sys_resource res ON res.status = 'enabled'
                WHERE r.code = 'ADMIN'
                  AND NOT EXISTS (
                      SELECT 1 FROM sys_role_resource rr
                      WHERE rr.role_id = r.id AND rr.resource_id = res.id AND rr.deleted = 0
                  )
                """);
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
        addColumnIfMissing("payroll_batch", "input_snapshot_hash",
                "ALTER TABLE payroll_batch ADD COLUMN `input_snapshot_hash` varchar(64) DEFAULT NULL COMMENT '薪资输入事实快照摘要' AFTER `batch_revision`");
        addColumnIfMissing("payroll_batch", "input_snapshot_json",
                "ALTER TABLE payroll_batch ADD COLUMN `input_snapshot_json` json DEFAULT NULL COMMENT '薪资输入事实完整快照' AFTER `input_snapshot_hash`");
        addColumnIfMissing("payroll_batch", "rule_snapshot_hash",
                "ALTER TABLE payroll_batch ADD COLUMN `rule_snapshot_hash` varchar(64) DEFAULT NULL COMMENT '薪资规则快照摘要' AFTER `input_snapshot_hash`");
        addColumnIfMissing("payroll_batch", "rule_snapshot_json",
                "ALTER TABLE payroll_batch ADD COLUMN `rule_snapshot_json` json DEFAULT NULL COMMENT '薪资规则完整快照' AFTER `rule_snapshot_hash`");
        addColumnIfMissing("payroll_batch", "calculation_engine_version",
                "ALTER TABLE payroll_batch ADD COLUMN `calculation_engine_version` varchar(64) DEFAULT NULL COMMENT '计算引擎版本' AFTER `rule_snapshot_hash`");
        addColumnIfMissing("payroll_batch", "payment_status",
                "ALTER TABLE payroll_batch ADD COLUMN `payment_status` varchar(32) DEFAULT NULL COMMENT '支付子域状态投影' AFTER `payment_batch_no`");
        addColumnIfMissing("payroll_line", "input_snapshot_hash",
                "ALTER TABLE payroll_line ADD COLUMN `input_snapshot_hash` varchar(64) DEFAULT NULL COMMENT '薪资输入事实快照摘要' AFTER `items_snapshot_json`");
        addColumnIfMissing("payroll_line", "batch_revision",
                "ALTER TABLE payroll_line ADD COLUMN `batch_revision` int NOT NULL DEFAULT 1 COMMENT '工资行所属批次版本号' AFTER `batch_id`");
        addColumnIfMissing("payroll_line", "rule_snapshot_hash",
                "ALTER TABLE payroll_line ADD COLUMN `rule_snapshot_hash` varchar(64) DEFAULT NULL COMMENT '薪资规则快照摘要' AFTER `input_snapshot_hash`");
        addColumnIfMissing("payroll_line", "calculation_engine_version",
                "ALTER TABLE payroll_line ADD COLUMN `calculation_engine_version` varchar(64) DEFAULT NULL COMMENT '计算引擎版本' AFTER `rule_snapshot_hash`");
        addIndexIfMissing("payroll_batch", "idx_calculation_status",
                "CREATE INDEX `idx_calculation_status` ON `payroll_batch` (`calculation_status`)");
        addIndexIfMissing("payroll_batch", "idx_batch_revision",
                "CREATE INDEX `idx_batch_revision` ON `payroll_batch` (`batch_revision`)");
        addIndexIfMissing("payroll_batch", "idx_payroll_payment_status",
                "CREATE INDEX `idx_payroll_payment_status` ON `payroll_batch` (`payment_status`)");
        addIndexIfMissing("payroll_line", "idx_batch_revision_employee",
                "CREATE INDEX `idx_batch_revision_employee` ON `payroll_line` (`batch_id`, `batch_revision`, `employee_id`)");
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

        createTableIfMissing("payroll_payment_failure", """
                CREATE TABLE `payroll_payment_failure` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `workflow_id` bigint NOT NULL COMMENT '审批工作流ID',
                  `payroll_batch_id` bigint DEFAULT NULL COMMENT '薪资批次ID',
                  `business_key` varchar(100) DEFAULT NULL COMMENT '审批业务Key',
                  `error_message` varchar(1000) DEFAULT NULL COMMENT '最近失败原因',
                  `status` varchar(20) NOT NULL DEFAULT 'unresolved' COMMENT 'unresolved/retrying/resolved',
                  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
                  `last_failed_time` datetime DEFAULT NULL COMMENT '最近失败时间',
                  `last_retry_time` datetime DEFAULT NULL COMMENT '最近重试时间',
                  `resolved_time` datetime DEFAULT NULL COMMENT '解决时间',
                  `payment_batch_no` varchar(64) DEFAULT NULL COMMENT '关联支付批次号',
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
                  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
                  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
                  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_workflow` (`workflow_id`),
                  KEY `idx_status_failed_time` (`status`,`last_failed_time`),
                  KEY `idx_payroll_batch` (`payroll_batch_id`),
                  KEY `idx_payment_batch_no` (`payment_batch_no`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资审批后置支付失败补偿表';
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

    private void migratePayrollRuleVersionColumns() {
        createTableIfMissing("salary_template_version", """
                CREATE TABLE `salary_template_version` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `template_id` bigint NOT NULL COMMENT '薪资规则包ID',
                  `version_no` bigint NOT NULL COMMENT '规则包数据版本号',
                  `name` varchar(100) NOT NULL COMMENT '规则包名称快照',
                  `type` varchar(20) NOT NULL COMMENT '适用用工类型',
                  `items_json` json DEFAULT NULL COMMENT '薪资项规则快照',
                  `tax_rule_json` json DEFAULT NULL COMMENT '税社保规则快照',
                  `status` varchar(20) DEFAULT 'disabled' COMMENT '生成快照时的规则包状态',
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  `create_by` varchar(50) DEFAULT NULL,
                  `update_by` varchar(50) DEFAULT NULL,
                  `deleted` tinyint(1) DEFAULT '0',
                  `version` int DEFAULT '0',
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_template_version` (`template_id`,`version_no`),
                  KEY `idx_template_type_version` (`type`,`version_no`),
                  KEY `idx_template_version_status` (`status`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资规则包不可变版本快照'
                """);

        addColumnIfMissing("pay_cycle", "rule_template_id",
                "ALTER TABLE pay_cycle ADD COLUMN `rule_template_id` bigint DEFAULT NULL COMMENT '绑定的薪资规则包ID' AFTER `type`");
        addColumnIfMissing("pay_cycle", "rule_template_version",
                "ALTER TABLE pay_cycle ADD COLUMN `rule_template_version` bigint DEFAULT NULL COMMENT '绑定的薪资规则包版本' AFTER `rule_template_id`");
        addColumnIfMissing("payroll_batch", "rule_template_id",
                "ALTER TABLE payroll_batch ADD COLUMN `rule_template_id` bigint DEFAULT NULL COMMENT '批次锁定的薪资规则包ID' AFTER `pay_cycle_id`");
        addColumnIfMissing("payroll_batch", "rule_template_version",
                "ALTER TABLE payroll_batch ADD COLUMN `rule_template_version` bigint DEFAULT NULL COMMENT '批次锁定的薪资规则包版本' AFTER `rule_template_id`");
        addColumnIfMissing("payroll_line", "template_version",
                "ALTER TABLE payroll_line ADD COLUMN `template_version` bigint DEFAULT NULL COMMENT '工资行使用的规则包版本' AFTER `template_id`");

        addIndexIfMissing("pay_cycle", "idx_cycle_rule_template",
                "CREATE INDEX `idx_cycle_rule_template` ON `pay_cycle` (`rule_template_id`, `rule_template_version`)");
        addIndexIfMissing("payroll_batch", "idx_batch_rule_template",
                "CREATE INDEX `idx_batch_rule_template` ON `payroll_batch` (`rule_template_id`, `rule_template_version`)");
        addIndexIfMissing("payroll_line", "idx_line_template_version",
                "CREATE INDEX `idx_line_template_version` ON `payroll_line` (`template_id`, `template_version`)");

        executeSqlSafely("UPDATE salary_template SET data_version = 1 WHERE data_version IS NULL");
        executeSqlSafely("INSERT INTO salary_template_version "
                + "(template_id, version_no, name, type, items_json, tax_rule_json, status, create_time, update_time, create_by, update_by, deleted, version) "
                + "SELECT st.id, COALESCE(st.data_version, 1), st.name, st.type, st.items_json, st.tax_rule_json, COALESCE(st.status, 'disabled'), "
                + "st.create_time, st.update_time, st.create_by, st.update_by, COALESCE(st.deleted, 0), COALESCE(st.version, 0) "
                + "FROM salary_template st WHERE NOT EXISTS (SELECT 1 FROM salary_template_version stv "
                + "WHERE stv.template_id = st.id AND stv.version_no = COALESCE(st.data_version, 1))");
        executeSqlSafely("UPDATE pay_cycle pc JOIN (SELECT pay_cycle_id, MIN(type) AS payroll_type "
                + "FROM payroll_batch WHERE deleted = 0 AND pay_cycle_id IS NOT NULL GROUP BY pay_cycle_id "
                + "HAVING COUNT(DISTINCT type) = 1) legacy ON legacy.pay_cycle_id = pc.id "
                + "SET pc.type = legacy.payroll_type WHERE pc.deleted = 0 "
                + "AND pc.type IN ('monthly', 'custom') "
                + "AND legacy.payroll_type IN ('full_time', 'part_time', 'contractor')");
        executeSqlSafely("UPDATE payroll_batch pb JOIN pay_cycle pc ON pc.id = pb.pay_cycle_id "
                + "SET pb.rule_template_id = pc.rule_template_id, pb.rule_template_version = pc.rule_template_version "
                + "WHERE pb.rule_template_id IS NULL AND pc.rule_template_id IS NOT NULL");
        executeSqlSafely("UPDATE payroll_line pl JOIN salary_template st ON st.id = pl.template_id "
                + "SET pl.template_version = COALESCE(st.data_version, 1) WHERE pl.template_version IS NULL");
    }

    private void migrateApprovalWorkflowColumns() {
        executeSqlSafely("ALTER TABLE approval_workflow MODIFY COLUMN `workflow_type` varchar(50) NOT NULL "
                + "COMMENT '流程类型(BATCH/ADHOC/OFFLINE/EMPLOYEE_PROFILE_CHANGE/PLATFORM_BIND)'");
        addColumnIfMissing("approval_workflow", "employee_id",
                "ALTER TABLE approval_workflow ADD COLUMN `employee_id` bigint DEFAULT NULL COMMENT '关联员工ID' AFTER `initiator_id`");
        addIndexIfMissing("approval_workflow", "idx_employee",
                "CREATE INDEX `idx_employee` ON `approval_workflow` (`employee_id`)");
    }

    /**
     * 薪酬合规基础表。生产环境默认只在显式开启 migration.runner 时执行，所有 DDL 均幂等。
     */
    private void migratePayrollComplianceFoundation() {
        createTableIfMissing("payroll_policy_package",
                "CREATE TABLE `payroll_policy_package` (" +
                        "`id` bigint NOT NULL AUTO_INCREMENT, `code` varchar(100) NOT NULL, `name` varchar(200) NOT NULL, " +
                        "`policy_type` varchar(32) NOT NULL, `region_code` varchar(32) DEFAULT NULL, " +
                        "`collection_entity_code` varchar(100) DEFAULT NULL, `person_category` varchar(64) DEFAULT NULL, " +
                        "`industry_risk_level` varchar(32) DEFAULT NULL, `effective_from` date NOT NULL, `effective_to` date DEFAULT NULL, " +
                        "`source_document` varchar(200) DEFAULT NULL, `source_url` varchar(500) DEFAULT NULL, `payload_json` json DEFAULT NULL, " +
                        "`status` varchar(20) NOT NULL DEFAULT 'draft', `version_no` bigint NOT NULL DEFAULT 1, `checksum` varchar(64) DEFAULT NULL, " +
                        "`reviewed_by` bigint DEFAULT NULL, `reviewed_at` datetime DEFAULT NULL, `published_by` bigint DEFAULT NULL, `published_at` datetime DEFAULT NULL, " +
                        "`create_time` datetime DEFAULT CURRENT_TIMESTAMP, `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                        "`create_by` varchar(50) DEFAULT NULL, `update_by` varchar(50) DEFAULT NULL, `deleted` tinyint(1) NOT NULL DEFAULT 0, `version` int NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY (`id`), UNIQUE KEY `uk_payroll_policy_code_version` (`code`,`version_no`,`deleted`), " +
                        "KEY `idx_payroll_policy_resolve` (`policy_type`,`region_code`,`status`,`effective_from`,`effective_to`)) " +
                        "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪酬法规政策版本'");
        createTableIfMissing("payroll_tax_bracket",
                "CREATE TABLE `payroll_tax_bracket` (" +
                        "`id` bigint NOT NULL AUTO_INCREMENT, `policy_id` bigint NOT NULL, `tax_year` int NOT NULL, `bracket_level` int NOT NULL, " +
                        "`upper_limit` decimal(18,2) DEFAULT NULL, `rate` decimal(12,8) NOT NULL, `quick_deduction` decimal(18,2) NOT NULL DEFAULT 0, " +
                        "`create_time` datetime DEFAULT CURRENT_TIMESTAMP, `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                        "`create_by` varchar(50) DEFAULT NULL, `update_by` varchar(50) DEFAULT NULL, `deleted` tinyint(1) NOT NULL DEFAULT 0, `version` int NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY (`id`), UNIQUE KEY `uk_payroll_tax_bracket` (`policy_id`,`tax_year`,`bracket_level`,`deleted`), " +
                        "KEY `idx_payroll_tax_bracket_policy` (`policy_id`,`tax_year`,`bracket_level`)) " +
                        "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='居民工资薪金累计预扣税率表'");
        createTableIfMissing("payroll_tax_deduction_declaration",
                "CREATE TABLE `payroll_tax_deduction_declaration` (" +
                        "`id` bigint NOT NULL AUTO_INCREMENT, `employee_id` bigint NOT NULL, `tax_year` int NOT NULL, `deduction_type` varchar(40) NOT NULL, " +
                        "`subject_key` varchar(128) DEFAULT NULL, `allocation_ratio` decimal(8,6) NOT NULL DEFAULT 1, `monthly_amount` decimal(18,2) DEFAULT NULL, " +
                        "`annual_amount` decimal(18,2) DEFAULT NULL, `effective_from` date DEFAULT NULL, `effective_to` date DEFAULT NULL, " +
                        "`credential_ref` varchar(255) DEFAULT NULL, `evidence_json` json DEFAULT NULL, `status` varchar(20) NOT NULL DEFAULT 'pending', " +
                        "`source_type` varchar(32) DEFAULT NULL, `create_time` datetime DEFAULT CURRENT_TIMESTAMP, `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                        "`create_by` varchar(50) DEFAULT NULL, `update_by` varchar(50) DEFAULT NULL, `deleted` tinyint(1) NOT NULL DEFAULT 0, `version` int NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY (`id`), KEY `idx_tax_deduction_employee_year` (`employee_id`,`tax_year`,`deduction_type`,`status`)) " +
                        "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个税专项附加扣除和其他扣除申报'");
        createTableIfMissing("payroll_tax_ledger",
                "CREATE TABLE `payroll_tax_ledger` (" +
                        "`id` bigint NOT NULL AUTO_INCREMENT, `employee_id` bigint NOT NULL, `withholding_entity_id` bigint DEFAULT NULL, " +
                        "`tax_year` int NOT NULL, `tax_month` int NOT NULL, `payroll_batch_id` bigint DEFAULT NULL, `payroll_batch_revision` int NOT NULL DEFAULT 1, `payroll_line_id` bigint DEFAULT NULL, " +
                        "`cumulative_income` decimal(18,2) NOT NULL DEFAULT 0, `cumulative_tax_exempt_income` decimal(18,2) NOT NULL DEFAULT 0, " +
                        "`cumulative_basic_deduction` decimal(18,2) NOT NULL DEFAULT 0, `cumulative_special_deduction` decimal(18,2) NOT NULL DEFAULT 0, " +
                        "`cumulative_special_additional` decimal(18,2) NOT NULL DEFAULT 0, `cumulative_other_deduction` decimal(18,2) NOT NULL DEFAULT 0, " +
                        "`cumulative_taxable_income` decimal(18,2) NOT NULL DEFAULT 0, `tax_rate` decimal(12,8) NOT NULL DEFAULT 0, `quick_deduction` decimal(18,2) NOT NULL DEFAULT 0, " +
                        "`cumulative_tax` decimal(18,2) NOT NULL DEFAULT 0, `cumulative_tax_reduction` decimal(18,2) NOT NULL DEFAULT 0, `cumulative_withheld_tax` decimal(18,2) NOT NULL DEFAULT 0, " +
                        "`current_withholding_tax` decimal(18,2) NOT NULL DEFAULT 0, `policy_id` bigint DEFAULT NULL, `calculation_hash` varchar(64) DEFAULT NULL, `status` varchar(20) NOT NULL DEFAULT 'draft', " +
                        "`create_time` datetime DEFAULT CURRENT_TIMESTAMP, `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `create_by` varchar(50) DEFAULT NULL, `update_by` varchar(50) DEFAULT NULL, `deleted` tinyint(1) NOT NULL DEFAULT 0, `version` int NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY (`id`), UNIQUE KEY `uk_tax_ledger_employee_period_batch_revision` (`employee_id`,`tax_year`,`tax_month`,`payroll_batch_id`,`payroll_batch_revision`,`deleted`), " +
                        "KEY `idx_tax_ledger_previous` (`employee_id`,`withholding_entity_id`,`tax_year`,`tax_month`,`status`)) " +
                        "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工年度累计个税台账'");
        createTableIfMissing("payroll_contribution_policy",
                "CREATE TABLE `payroll_contribution_policy` (" +
                        "`id` bigint NOT NULL AUTO_INCREMENT, `code` varchar(100) NOT NULL, `region_code` varchar(32) NOT NULL, `collection_entity_code` varchar(100) DEFAULT NULL, " +
                        "`contribution_type` varchar(40) NOT NULL, `person_category` varchar(64) DEFAULT NULL, `household_type` varchar(32) DEFAULT NULL, `industry_risk_level` varchar(32) DEFAULT NULL, " +
                        "`effective_from` date NOT NULL, `effective_to` date DEFAULT NULL, `base_min` decimal(18,2) DEFAULT NULL, `base_max` decimal(18,2) DEFAULT NULL, " +
                        "`employer_rate` decimal(12,8) NOT NULL DEFAULT 0, `employee_rate` decimal(12,8) NOT NULL DEFAULT 0, `employer_fixed_amount` decimal(18,2) NOT NULL DEFAULT 0, `employee_fixed_amount` decimal(18,2) NOT NULL DEFAULT 0, " +
                        "`rounding_mode` varchar(20) DEFAULT 'HALF_UP', `minimum_amount` decimal(18,2) DEFAULT NULL, `source_document` varchar(200) DEFAULT NULL, `source_url` varchar(500) DEFAULT NULL, `status` varchar(20) NOT NULL DEFAULT 'draft', `version_no` bigint NOT NULL DEFAULT 1, `reviewed_by` bigint DEFAULT NULL, `reviewed_at` datetime DEFAULT NULL, `published_by` bigint DEFAULT NULL, `published_at` datetime DEFAULT NULL, " +
                        "`create_time` datetime DEFAULT CURRENT_TIMESTAMP, `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `create_by` varchar(50) DEFAULT NULL, `update_by` varchar(50) DEFAULT NULL, `deleted` tinyint(1) NOT NULL DEFAULT 0, `version` int NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY (`id`), UNIQUE KEY `uk_contribution_policy_version` (`code`,`version_no`,`deleted`), KEY `idx_contribution_policy_resolve` (`region_code`,`contribution_type`,`effective_from`,`effective_to`,`status`)) " +
                        "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='地区五险一金政策参数'");
        createTableIfMissing("payroll_enrollment",
                "CREATE TABLE `payroll_enrollment` (" +
                        "`id` bigint NOT NULL AUTO_INCREMENT, `employee_id` bigint NOT NULL, `contribution_type` varchar(40) NOT NULL, `region_code` varchar(32) NOT NULL, `collection_entity_code` varchar(100) DEFAULT NULL, `account_no_encrypted` text, `effective_from` date NOT NULL, `effective_to` date DEFAULT NULL, `status` varchar(20) NOT NULL DEFAULT 'active', `is_primary` tinyint(1) NOT NULL DEFAULT 1, `event_type` varchar(32) DEFAULT NULL, `policy_id` bigint DEFAULT NULL, `create_time` datetime DEFAULT CURRENT_TIMESTAMP, `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `create_by` varchar(50) DEFAULT NULL, `update_by` varchar(50) DEFAULT NULL, `deleted` tinyint(1) NOT NULL DEFAULT 0, `version` int NOT NULL DEFAULT 0, PRIMARY KEY (`id`), KEY `idx_enrollment_employee_type` (`employee_id`,`contribution_type`,`status`), KEY `idx_enrollment_period` (`effective_from`,`effective_to`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工多地参保/公积金关系'");
        createTableIfMissing("payroll_contribution_record",
                "CREATE TABLE `payroll_contribution_record` (`id` bigint NOT NULL AUTO_INCREMENT, `payroll_batch_id` bigint DEFAULT NULL, `payroll_line_id` bigint DEFAULT NULL, `employee_id` bigint NOT NULL, `contribution_type` varchar(40) NOT NULL, `region_code` varchar(32) NOT NULL, `policy_id` bigint DEFAULT NULL, `declared_wage` decimal(18,2) DEFAULT NULL, `contribution_base` decimal(18,2) DEFAULT NULL, `employer_rate` decimal(12,8) DEFAULT NULL, `employee_rate` decimal(12,8) DEFAULT NULL, `employer_amount` decimal(18,2) NOT NULL DEFAULT 0, `employee_amount` decimal(18,2) NOT NULL DEFAULT 0, `adjustment_of_id` bigint DEFAULT NULL, `status` varchar(20) NOT NULL DEFAULT 'calculated', `calculation_hash` varchar(64) DEFAULT NULL, `create_time` datetime DEFAULT CURRENT_TIMESTAMP, `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `create_by` varchar(50) DEFAULT NULL, `update_by` varchar(50) DEFAULT NULL, `deleted` tinyint(1) NOT NULL DEFAULT 0, `version` int NOT NULL DEFAULT 0, PRIMARY KEY (`id`), KEY `idx_contribution_record_batch` (`payroll_batch_id`,`payroll_line_id`), KEY `idx_contribution_record_employee` (`employee_id`,`contribution_type`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='五险一金缴费计算结果'");
        createTableIfMissing("payroll_calculation_trace",
                "CREATE TABLE `payroll_calculation_trace` (`id` bigint NOT NULL AUTO_INCREMENT, `payroll_batch_id` bigint DEFAULT NULL, `payroll_line_id` bigint DEFAULT NULL, `employee_id` bigint DEFAULT NULL, `sequence` int NOT NULL, `step_code` varchar(64) NOT NULL, `item_code` varchar(64) DEFAULT NULL, `input_json` json DEFAULT NULL, `output_value` decimal(18,6) DEFAULT NULL, `formula` text, `rule_version` varchar(128) DEFAULT NULL, `source_ref` varchar(255) DEFAULT NULL, `rounding_mode` varchar(20) DEFAULT NULL, `checksum` varchar(64) DEFAULT NULL, `create_time` datetime DEFAULT CURRENT_TIMESTAMP, `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `create_by` varchar(50) DEFAULT NULL, `update_by` varchar(50) DEFAULT NULL, `deleted` tinyint(1) NOT NULL DEFAULT 0, `version` int NOT NULL DEFAULT 0, PRIMARY KEY (`id`), UNIQUE KEY `uk_calculation_trace_line_sequence` (`payroll_line_id`,`sequence`,`deleted`), KEY `idx_calculation_trace_batch_line` (`payroll_batch_id`,`payroll_line_id`,`sequence`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪酬计算逐步证据链'");

        addColumnIfMissing("payroll_batch", "pay_date", "ALTER TABLE payroll_batch ADD COLUMN `pay_date` date DEFAULT NULL COMMENT '实际发放日期' AFTER `currency`");
        addColumnIfMissing("payroll_batch", "tax_year", "ALTER TABLE payroll_batch ADD COLUMN `tax_year` int DEFAULT NULL COMMENT '税务年度' AFTER `pay_date`");
        addColumnIfMissing("payroll_batch", "tax_month", "ALTER TABLE payroll_batch ADD COLUMN `tax_month` int DEFAULT NULL COMMENT '税款所属月' AFTER `tax_year`");
        addColumnIfMissing("payroll_batch", "tax_withholding_entity_id", "ALTER TABLE payroll_batch ADD COLUMN `tax_withholding_entity_id` bigint DEFAULT NULL COMMENT '扣缴义务人' AFTER `tax_month`");
        addColumnIfMissing("payroll_batch", "tax_basic_deduction_months", "ALTER TABLE payroll_batch ADD COLUMN `tax_basic_deduction_months` int DEFAULT NULL COMMENT '本单位任职受雇月份数' AFTER `tax_withholding_entity_id`");
        addColumnIfMissing("payroll_batch", "policy_package_id", "ALTER TABLE payroll_batch ADD COLUMN `policy_package_id` bigint DEFAULT NULL COMMENT '政策包版本' AFTER `tax_basic_deduction_months`");
        addColumnIfMissing("payroll_batch", "result_hash", "ALTER TABLE payroll_batch ADD COLUMN `result_hash` varchar(64) DEFAULT NULL COMMENT '结算结果摘要' AFTER `remark`");
        addColumnIfMissing("payroll_batch", "input_frozen_at", "ALTER TABLE payroll_batch ADD COLUMN `input_frozen_at` datetime DEFAULT NULL COMMENT '输入冻结时间' AFTER `result_hash`");
        addColumnIfMissing("payroll_batch", "locked_at", "ALTER TABLE payroll_batch ADD COLUMN `locked_at` datetime DEFAULT NULL COMMENT '结果锁定时间' AFTER `input_frozen_at`");
        addColumnIfMissing("payroll_batch", "closed_at", "ALTER TABLE payroll_batch ADD COLUMN `closed_at` datetime DEFAULT NULL COMMENT '关账时间' AFTER `locked_at`");
        addColumnIfMissing("payroll_batch", "immutable_flag", "ALTER TABLE payroll_batch ADD COLUMN `immutable_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '结果是否不可变' AFTER `closed_at`");
        addColumnIfMissing("payroll_batch", "adjustment_of_batch_id", "ALTER TABLE payroll_batch ADD COLUMN `adjustment_of_batch_id` bigint DEFAULT NULL COMMENT '调整所基于的原批次' AFTER `immutable_flag`");
        addColumnIfMissing("payroll_line", "tax_breakdown_json", "ALTER TABLE payroll_line ADD COLUMN `tax_breakdown_json` json DEFAULT NULL COMMENT '个税累计计算解释快照' AFTER `net_amount`");
        addColumnIfMissing("payroll_line", "employee_no_snapshot", "ALTER TABLE payroll_line ADD COLUMN `employee_no_snapshot` varchar(50) DEFAULT NULL COMMENT '核算时员工工号快照' AFTER `employee_id`");
        addColumnIfMissing("payroll_line", "employee_name_snapshot", "ALTER TABLE payroll_line ADD COLUMN `employee_name_snapshot` varchar(100) DEFAULT NULL COMMENT '核算时员工姓名快照' AFTER `employee_no_snapshot`");
        addColumnIfMissing("payroll_line", "department_snapshot", "ALTER TABLE payroll_line ADD COLUMN `department_snapshot` varchar(500) DEFAULT NULL COMMENT '核算时部门快照' AFTER `employee_name_snapshot`");
        addColumnIfMissing("salary_item", "tax_category", "ALTER TABLE salary_item ADD COLUMN `tax_category` varchar(40) DEFAULT NULL COMMENT '税务分类' AFTER `taxable`");
        addColumnIfMissing("salary_item", "tax_exempt", "ALTER TABLE salary_item ADD COLUMN `tax_exempt` tinyint(1) DEFAULT 0 COMMENT '是否免税' AFTER `tax_category`");
        addColumnIfMissing("salary_item", "pension_base", "ALTER TABLE salary_item ADD COLUMN `pension_base` tinyint(1) DEFAULT 0 COMMENT '是否计入养老基数' AFTER `tax_exempt`");
        addColumnIfMissing("salary_item", "medical_base", "ALTER TABLE salary_item ADD COLUMN `medical_base` tinyint(1) DEFAULT 0 COMMENT '是否计入医疗基数' AFTER `pension_base`");
        addColumnIfMissing("salary_item", "unemployment_base", "ALTER TABLE salary_item ADD COLUMN `unemployment_base` tinyint(1) DEFAULT 0 COMMENT '是否计入失业基数' AFTER `medical_base`");
        addColumnIfMissing("salary_item", "work_injury_base", "ALTER TABLE salary_item ADD COLUMN `work_injury_base` tinyint(1) DEFAULT 0 COMMENT '是否计入工伤基数' AFTER `unemployment_base`");
        addColumnIfMissing("salary_item", "maternity_base", "ALTER TABLE salary_item ADD COLUMN `maternity_base` tinyint(1) DEFAULT 0 COMMENT '是否计入生育基数' AFTER `work_injury_base`");
        addColumnIfMissing("salary_item", "housing_fund_base", "ALTER TABLE salary_item ADD COLUMN `housing_fund_base` tinyint(1) DEFAULT 0 COMMENT '是否计入公积金基数' AFTER `maternity_base`");
        addColumnIfMissing("salary_item", "formula_json", "ALTER TABLE salary_item ADD COLUMN `formula_json` json DEFAULT NULL COMMENT '受限公式AST/DSL' AFTER `housing_fund_base`");
        addColumnIfMissing("salary_item", "precision_scale", "ALTER TABLE salary_item ADD COLUMN `precision_scale` int DEFAULT 2 COMMENT '计算精度' AFTER `formula_json`");
        addColumnIfMissing("salary_item", "rounding_mode", "ALTER TABLE salary_item ADD COLUMN `rounding_mode` varchar(20) DEFAULT 'HALF_UP' COMMENT '舍入方式' AFTER `precision_scale`");
        addColumnIfMissing("salary_item", "effective_from", "ALTER TABLE salary_item ADD COLUMN `effective_from` date DEFAULT NULL COMMENT '生效日期' AFTER `rounding_mode`");
        addColumnIfMissing("salary_item", "effective_to", "ALTER TABLE salary_item ADD COLUMN `effective_to` date DEFAULT NULL COMMENT '失效日期' AFTER `effective_from`");
        addColumnIfMissing("payroll_import_item", "source_external_key", "ALTER TABLE payroll_import_item ADD COLUMN `source_external_key` varchar(191) DEFAULT NULL COMMENT '来源系统业务幂等键' AFTER `error_msg`");
        addColumnIfMissing("payroll_import_item", "business_date", "ALTER TABLE payroll_import_item ADD COLUMN `business_date` date DEFAULT NULL COMMENT '业务日期' AFTER `source_external_key`");
        addColumnIfMissing("payroll_import_item", "source_version", "ALTER TABLE payroll_import_item ADD COLUMN `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本' AFTER `business_date`");
        addColumnIfMissing("payroll_import_item", "approval_status", "ALTER TABLE payroll_import_item ADD COLUMN `approval_status` varchar(20) DEFAULT 'approved' COMMENT '事实审批状态' AFTER `source_version`");
        addColumnIfMissing("payroll_import_item", "imported_at", "ALTER TABLE payroll_import_item ADD COLUMN `imported_at` datetime DEFAULT NULL COMMENT '导入时间' AFTER `approval_status`");
        addColumnIfMissing("payroll_contribution_policy", "reviewed_by", "ALTER TABLE payroll_contribution_policy ADD COLUMN `reviewed_by` bigint DEFAULT NULL AFTER `version_no`");
        addColumnIfMissing("payroll_contribution_policy", "reviewed_at", "ALTER TABLE payroll_contribution_policy ADD COLUMN `reviewed_at` datetime DEFAULT NULL AFTER `reviewed_by`");
        addColumnIfMissing("payroll_contribution_policy", "published_by", "ALTER TABLE payroll_contribution_policy ADD COLUMN `published_by` bigint DEFAULT NULL AFTER `reviewed_at`");
        addColumnIfMissing("payroll_contribution_policy", "published_at", "ALTER TABLE payroll_contribution_policy ADD COLUMN `published_at` datetime DEFAULT NULL AFTER `published_by`");

        executeSqlSafely("INSERT INTO payroll_policy_package (code,name,policy_type,region_code,effective_from,source_document,source_url,payload_json,status,version_no,checksum) " +
                "SELECT 'CN.RESIDENT_WAGE_WITHHOLDING','居民个人工资薪金累计预扣预缴','tax','CN','2019-01-01','国家税务总局公告2018年第61号','https://fgk.chinatax.gov.cn/zcfgk/c100015/c5200946/content.html'," +
                "JSON_OBJECT('basicDeductionPerMonth',5000,'deductions',JSON_OBJECT(" +
                        "'infant_care',JSON_OBJECT('monthlyPerSubject',2000,'effectiveFrom','2023-01-01')," +
                        "'child_education',JSON_OBJECT('monthlyPerSubject',2000,'effectiveFrom','2023-01-01')," +
                        "'continuing_education',JSON_OBJECT('monthlyAmount',400,'vocationalAnnualAmount',3600)," +
                        "'major_medical',JSON_OBJECT('annualThreshold',15000,'annualLimit',80000,'settlementOnly',true)," +
                        "'housing_loan_interest',JSON_OBJECT('monthlyAmount',1000,'maxMonths',240)," +
                        "'rent',JSON_OBJECT('monthlyAmounts',JSON_ARRAY(800,1100,1500))," +
                        "'elderly_care',JSON_OBJECT('singleChildMonthlyAmount',3000,'nonSingleTotalMonthlyAmount',3000,'perPersonLimit',1500)," +
                        "'individual_pension',JSON_OBJECT('annualLimit',12000,'effectiveFrom','2024-01-01')" +
                        "),'annualBonusPolicyUntil','2027-12-31'),'published',1,SHA2('CN.RESIDENT_WAGE_WITHHOLDING@1',256) " +
                "WHERE NOT EXISTS (SELECT 1 FROM payroll_policy_package WHERE code='CN.RESIDENT_WAGE_WITHHOLDING' AND version_no=1 AND deleted=0)");
        executeSqlSafely("INSERT IGNORE INTO payroll_tax_bracket (policy_id,tax_year,bracket_level,upper_limit,rate,quick_deduction) " +
                "SELECT p.id,2026,1,36000,0.03,0 FROM payroll_policy_package p WHERE p.code='CN.RESIDENT_WAGE_WITHHOLDING' AND p.version_no=1 AND p.deleted=0 " +
                "UNION ALL SELECT p.id,2026,2,144000,0.10,2520 FROM payroll_policy_package p WHERE p.code='CN.RESIDENT_WAGE_WITHHOLDING' AND p.version_no=1 AND p.deleted=0 " +
                "UNION ALL SELECT p.id,2026,3,300000,0.20,16920 FROM payroll_policy_package p WHERE p.code='CN.RESIDENT_WAGE_WITHHOLDING' AND p.version_no=1 AND p.deleted=0 " +
                "UNION ALL SELECT p.id,2026,4,420000,0.25,31920 FROM payroll_policy_package p WHERE p.code='CN.RESIDENT_WAGE_WITHHOLDING' AND p.version_no=1 AND p.deleted=0 " +
                "UNION ALL SELECT p.id,2026,5,660000,0.30,52920 FROM payroll_policy_package p WHERE p.code='CN.RESIDENT_WAGE_WITHHOLDING' AND p.version_no=1 AND p.deleted=0 " +
                "UNION ALL SELECT p.id,2026,6,960000,0.35,85920 FROM payroll_policy_package p WHERE p.code='CN.RESIDENT_WAGE_WITHHOLDING' AND p.version_no=1 AND p.deleted=0 " +
                "UNION ALL SELECT p.id,2026,7,NULL,0.45,181920 FROM payroll_policy_package p WHERE p.code='CN.RESIDENT_WAGE_WITHHOLDING' AND p.version_no=1 AND p.deleted=0");
        executeSqlSafely("UPDATE salary_template SET status='disabled' WHERE deleted=0 AND status='enabled' AND (tax_rule_json IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(tax_rule_json,'$.tax.mode')) <> 'cumulative_withholding')");
    }

    private void migratePayrollIntegrityHardening() {
        createTableIfMissing("app_data_grant",
                "CREATE TABLE `app_data_grant` (" +
                        "`id` bigint NOT NULL AUTO_INCREMENT, `app_id` bigint NOT NULL, " +
                        "`scope_type` varchar(32) NOT NULL, `scope_value` varchar(128) NOT NULL, " +
                        "`status` varchar(20) NOT NULL DEFAULT 'active', " +
                        "`create_time` datetime DEFAULT CURRENT_TIMESTAMP, `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                        "`create_by` varchar(50) DEFAULT NULL, `update_by` varchar(50) DEFAULT NULL, " +
                        "`deleted` tinyint(1) NOT NULL DEFAULT 0, `version` int NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY (`id`), UNIQUE KEY `uk_app_data_grant` (`app_id`,`scope_type`,`scope_value`,`deleted`), " +
                        "KEY `idx_app_data_grant_active` (`app_id`,`status`,`scope_type`)) " +
                        "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方应用数据范围授权'");
        addColumnIfMissing("payroll_tax_ledger", "payroll_batch_revision",
                "ALTER TABLE payroll_tax_ledger ADD COLUMN `payroll_batch_revision` int NOT NULL DEFAULT 1 COMMENT '工资批次版本' AFTER `payroll_batch_id`");
        addColumnIfMissing("payroll_tax_deduction_declaration", "policy_id",
                "ALTER TABLE payroll_tax_deduction_declaration ADD COLUMN `policy_id` bigint DEFAULT NULL COMMENT '扣除政策版本' AFTER `source_type`");
        addColumnIfMissing("payroll_tax_deduction_declaration", "facts_json",
                "ALTER TABLE payroll_tax_deduction_declaration ADD COLUMN `facts_json` json DEFAULT NULL COMMENT '扣除事实JSON' AFTER `evidence_json`");
        dropIndexIfExists("payroll_tax_ledger", "uk_tax_ledger_employee_period_batch");
        addIndexIfMissing("payroll_tax_ledger", "uk_tax_ledger_employee_period_batch_revision",
                "CREATE UNIQUE INDEX `uk_tax_ledger_employee_period_batch_revision` ON `payroll_tax_ledger` (`employee_id`,`tax_year`,`tax_month`,`payroll_batch_id`,`payroll_batch_revision`,`deleted`)");
        executeSqlSafely("UPDATE payroll_policy_package SET payload_json = JSON_SET(COALESCE(payload_json, JSON_OBJECT()), '$.deductions', JSON_OBJECT(" +
                "'infant_care', JSON_OBJECT('monthlyPerSubject', 2000, 'effectiveFrom', '2023-01-01')," +
                "'child_education', JSON_OBJECT('monthlyPerSubject', 2000, 'effectiveFrom', '2023-01-01')," +
                "'continuing_education', JSON_OBJECT('monthlyAmount', 400, 'vocationalAnnualAmount', 3600)," +
                "'major_medical', JSON_OBJECT('annualThreshold', 15000, 'annualLimit', 80000, 'settlementOnly', TRUE)," +
                "'housing_loan_interest', JSON_OBJECT('monthlyAmount', 1000, 'maxMonths', 240)," +
                "'rent', JSON_OBJECT('monthlyAmounts', JSON_ARRAY(800, 1100, 1500))," +
                "'elderly_care', JSON_OBJECT('singleChildMonthlyAmount', 3000, 'nonSingleTotalMonthlyAmount', 3000, 'perPersonLimit', 1500)," +
                "'individual_pension', JSON_OBJECT('annualLimit', 12000, 'effectiveFrom', '2024-01-01'))) " +
                "WHERE code = 'CN.RESIDENT_WAGE_WITHHOLDING' AND version_no = 1 AND deleted = 0 " +
                "AND JSON_EXTRACT(payload_json, '$.deductions') IS NULL");
        executeSqlSafely("UPDATE payroll_line pl JOIN employee e ON e.id = pl.employee_id SET " +
                "pl.employee_no_snapshot = COALESCE(pl.employee_no_snapshot, e.employee_id), " +
                "pl.employee_name_snapshot = COALESCE(pl.employee_name_snapshot, e.name), " +
                "pl.department_snapshot = COALESCE(pl.department_snapshot, e.department) " +
                "WHERE pl.deleted = 0 AND (pl.employee_no_snapshot IS NULL OR pl.employee_name_snapshot IS NULL OR pl.department_snapshot IS NULL)");
    }

    private void migratePayrollComplianceResources() {
        executeSqlSafely("""
                INSERT INTO sys_resource
                    (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time)
                SELECT 'VIEW', 'view.payroll.compliance', '合规核算', '/payroll/compliance', 'payroll/Compliance',
                       'SafetyCertificate', (SELECT id FROM sys_resource WHERE code = 'menu.system.payroll' LIMIT 1),
                       3, '{"roles":["ADMIN","FINANCE"]}', 'enabled', NOW(), NOW()
                WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'view.payroll.compliance')
                """);
        executeSqlSafely("""
                INSERT INTO sys_resource
                    (type, code, name, path, parent_id, order_num, props_json, status, create_time, update_time)
                SELECT 'API', 'api.payroll.compliance.tax', '薪酬合规计算', '/api/payroll/compliance/*',
                       (SELECT id FROM sys_resource WHERE code = 'menu.system.payroll' LIMIT 1),
                       320, '{"roles":["ADMIN","FINANCE"]}', 'enabled', NOW(), NOW()
                WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'api.payroll.compliance.tax')
                """);
        executeSqlSafely("""
                INSERT INTO sys_role_resource (role_id, resource_id, actions_json, create_time, create_by)
                SELECT role.id, resource.id, '["*"]', NOW(), 'payroll_compliance_migration'
                FROM sys_role role
                JOIN sys_resource resource
                  ON resource.code IN ('view.payroll.compliance', 'api.payroll.compliance.tax')
                WHERE role.code IN ('ADMIN', 'FINANCE')
                  AND NOT EXISTS (
                      SELECT 1 FROM sys_role_resource existing
                      WHERE existing.role_id = role.id AND existing.resource_id = resource.id AND existing.deleted = 0
                  )
                """);
    }

    /**
     * 旧薪酬页面已经从前端移除，数据库菜单也必须指向当前运营工作台，避免动态菜单再次加载旧组件。
     */
    private void retireLegacyPayrollContent() {
        int changed = 0;
        changed += executeSqlSafely("UPDATE sys_resource SET name='薪酬运营', component='payroll/Operations', update_by='legacy_payroll_retirement', update_time=NOW() " +
                "WHERE code='menu.payroll.batches' AND deleted=0 AND (component IS NULL OR component <> 'payroll/Operations')");
        changed += executeSqlSafely("UPDATE sys_resource SET status='disabled', update_by='legacy_payroll_retirement', update_time=NOW() " +
                "WHERE deleted=0 AND status <> 'disabled' AND code IN ('view.payroll.batches','view.payroll.cycles','view.payroll.templates','menu.payroll.import','menu.payroll.reports','business')");
        changed += executeSqlSafely("UPDATE sys_role_resource SET deleted=1, update_by='legacy_payroll_retirement', update_time=NOW() " +
                "WHERE deleted=0 AND resource_id IN (SELECT id FROM sys_resource WHERE code IN ('view.payroll.batches','view.payroll.cycles','view.payroll.templates','menu.payroll.import','menu.payroll.reports','business'))");
        changed += executeSqlSafely("UPDATE sys_user_resource SET deleted=1, update_by='legacy_payroll_retirement', update_time=NOW() " +
                "WHERE deleted=0 AND resource_id IN (SELECT id FROM sys_resource WHERE code IN ('view.payroll.batches','view.payroll.cycles','view.payroll.templates','menu.payroll.import','menu.payroll.reports','business'))");
        if (changed > 0) {
            executeSqlSafely("UPDATE sys_user SET permission_version=COALESCE(permission_version,0)+1, update_by='legacy_payroll_retirement', update_time=NOW() " +
                    "WHERE status='active'");
        }
    }

    /**
     * 将前端静态路由与 RBAC 页面资源收敛到同一份目录。
     *
     * 资源 SQL 文件主要用于人工部署，生产容器则由本 runner 负责幂等校正，避免旧数据库只执行了
     * 部分 migration 后出现“菜单可见但页面组件不存在”或“页面存在但被 403 拦截”的半配置状态。
     */
    private void migrateFrontendRouteResources() {
        boolean changed = false;

        changed |= ensureFrontendResource("MENU", "dashboard", "工作台", "/", "dashboard/Dashboard", "dashboard",
                null, 1, "{\"roles\":[\"ADMIN\",\"FINANCE\",\"HR\"],\"keepAlive\":true,\"affix\":true}");
        changed |= ensureFrontendResource("MENU", "employees", "员工管理", "/employees", "employees/List", "team",
                null, 10, "{\"keepAlive\":true}");
        changed |= ensureFrontendResource("MENU", "payments", "支付管理", null, null, "wallet",
                null, 21, "{\"keepAlive\":true}");
        changed |= ensureFrontendResource("MENU", "payments.batches", "支付批次", "/payments/batches", "payments/Batches", "wallet",
                "payments", 22, "{\"keepAlive\":true}");
        changed |= ensureFrontendResource("MENU", "system", "系统配置", null, null, "control",
                null, 90, "{\"keepAlive\":true}");
        changed |= ensureFrontendResource("MENU", "system.integration", "集成配置", "/system/integration", "system/IntegrationConfig", "global",
                "system", 91, "{}");
        changed |= ensureFrontendResource("MENU", "system.org-sync", "组织同步", "/system/org-sync", "system/OrgSync", "sync",
                "system", 92, "{}");
        changed |= ensureFrontendResource("MENU", "admin", "系统管理", null, null, "setting",
                null, 80, "{\"roles\":[\"ADMIN\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("MENU", "admin.auth-center", "授权中心", "/admin/auth-center", "admin/auth-center/users/UserList", "safety-certificate",
                "admin", 80, "{\"roles\":[\"ADMIN\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("MENU", "admin.user-binding", "用户绑定", "/admin/user-binding", "admin/UserBinding", "user",
                "admin", 81, "{\"roles\":[\"ADMIN\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("MENU", "admin.roles", "角色管理", "/admin/roles", "admin/auth-center/roles/RoleList", "team",
                "admin", 82, "{\"roles\":[\"ADMIN\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("MENU", "admin.resources.v2", "菜单管理", "/admin/resources-v2", "admin/ResourcesV2", "menu",
                "admin", 83, "{\"roles\":[\"ADMIN\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("MENU", "admin.app-registry", "外部应用注册", "/admin/app-registry", "admin/AppRegistry", "appstore",
                "admin", 84, "{\"roles\":[\"ADMIN\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("MENU", "admin.audit", "审计日志", "/admin/audit-logs", "admin/AuditLogs", "file-search",
                "admin", 85, "{\"roles\":[\"ADMIN\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("MENU", "admin.monitor", "系统监控", "/admin/monitor", "admin/Monitor", "dashboard",
                "admin", 86, "{\"roles\":[\"ADMIN\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("MENU", "admin.tasks", "任务调度", "/admin/tasks", "admin/TaskSchedules", "schedule",
                "admin", 87, "{\"roles\":[\"ADMIN\"],\"keepAlive\":true}");

        changed |= ensureFrontendResource("MENU", "menu.system.payroll", "薪酬管理", null, null, "money-collect",
                null, 30, "{\"roles\":[\"ADMIN\",\"FINANCE\",\"HR\",\"MANAGER\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("MENU", "menu.payroll.batches", "薪酬运营", "/payroll/batches", "payroll/Operations", "audit",
                "menu.system.payroll", 31, "{\"roles\":[\"ADMIN\",\"FINANCE\",\"HR\",\"MANAGER\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("MENU", "menu.approval", "审批管理", "/approval/workflows", "approval/Workflows", "safety-certificate",
                null, 25, "{\"keepAlive\":true}");

        changed |= ensureFrontendResource("VIEW", "view.employees.me", "员工自助资料", "/employees/me", "employees/Profile", null,
                "employees", 30, "{\"roles\":[\"ADMIN\",\"EMPLOYEE\"],\"hidden\":true}");
        changed |= ensureFrontendResource("VIEW", "payments.batch.detail", "批次详情", "/payments/batches/:batchNo", "payments/BatchDetail", null,
                "payments.batches", 41, "{\"hidden\":true}");
        changed |= ensureFrontendResource("VIEW", "view.payroll.batch.entry", "批次录入", "/payroll/batches/:batchId/entry", "payroll/Entry", null,
                "menu.system.payroll", 32, "{\"hidden\":true}");
        changed |= ensureFrontendResource("VIEW", "view.payroll.compliance", "合规核算", "/payroll/compliance", "payroll/Compliance", "safety-certificate",
                "menu.system.payroll", 34, "{\"roles\":[\"ADMIN\",\"FINANCE\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("VIEW", "view.payroll.rules", "规则管理", "/payroll/rules", "payroll/Rules", "file-protect",
                "menu.system.payroll", 35, "{\"roles\":[\"ADMIN\",\"FINANCE\",\"HR\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("VIEW", "view.payroll.calendar", "薪酬日历", "/payroll/calendar", "payroll/Calendar", "calendar",
                "menu.system.payroll", 36, "{\"roles\":[\"ADMIN\",\"FINANCE\",\"HR\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("VIEW", "view.payroll.confirmations", "确认工作台", "/payroll/confirmations", "payroll/Confirmations", "check-circle",
                "menu.system.payroll", 38, "{\"roles\":[\"ADMIN\",\"FINANCE\",\"HR\",\"MANAGER\",\"EMPLOYEE\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("VIEW", "view.payroll.distributions", "薪酬发放", "/payroll/distributions", "payroll/Distributions", "bank",
                "menu.system.payroll", 39, "{\"roles\":[\"ADMIN\",\"FINANCE\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("VIEW", "view.payroll.reconciliations", "薪酬对账", "/payroll/reconciliations", "payroll/Reconciliations", "audit",
                "menu.system.payroll", 40, "{\"roles\":[\"ADMIN\",\"FINANCE\"],\"keepAlive\":true}");
        changed |= ensureFrontendResource("VIEW", "view.payroll.batch.ledger", "薪酬批次台账", "/payroll/batches/:batchId/ledger", "payroll/BatchLedger", null,
                "menu.system.payroll", 42, "{\"roles\":[\"ADMIN\",\"FINANCE\",\"MANAGER\"],\"hidden\":true}");
        changed |= ensureFrontendResource("VIEW", "view.payroll.batch.manager", "经理核对", "/payroll/batches/:batchId/manager-review", "payroll/ManagerReview", null,
                "menu.system.payroll", 43, "{\"roles\":[\"ADMIN\",\"FINANCE\",\"MANAGER\"],\"hidden\":true}");
        changed |= ensureFrontendResource("VIEW", "view.payroll.pt-readonly", "兼职薪酬查询", "/payroll/pt-readonly", "payroll/PartTimeReadonly", "search",
                "menu.system.payroll", 44, "{\"roles\":[\"ADMIN\",\"FINANCE\"],\"keepAlive\":true}");

        changed |= grantAllEnabledFrontendResourcesToAdmin();
        changed |= grantFrontendResource("FINANCE", "dashboard");
        changed |= grantFrontendResource("FINANCE", "employees");
        changed |= grantFrontendResource("FINANCE", "payments");
        changed |= grantFrontendResource("FINANCE", "payments.batches");
        changed |= grantFrontendResource("FINANCE", "menu.approval");
        changed |= grantFrontendResource("FINANCE", "menu.system.payroll");
        changed |= grantFrontendResource("FINANCE", "menu.payroll.batches");
        changed |= grantFrontendResource("FINANCE", "view.payroll.batch.entry");
        changed |= grantFrontendResource("FINANCE", "view.payroll.compliance");
        changed |= grantFrontendResource("FINANCE", "view.payroll.rules");
        changed |= grantFrontendResource("FINANCE", "view.payroll.calendar");
        changed |= grantFrontendResource("FINANCE", "view.payroll.confirmations");
        changed |= grantFrontendResource("FINANCE", "view.payroll.distributions");
        changed |= grantFrontendResource("FINANCE", "view.payroll.reconciliations");
        changed |= grantFrontendResource("FINANCE", "view.payroll.batch.ledger");
        changed |= grantFrontendResource("FINANCE", "view.payroll.batch.manager");
        changed |= grantFrontendResource("FINANCE", "view.payroll.pt-readonly");

        changed |= grantFrontendResource("HR", "dashboard");
        changed |= grantFrontendResource("HR", "employees");
        changed |= grantFrontendResource("HR", "menu.system.payroll");
        changed |= grantFrontendResource("HR", "menu.payroll.batches");
        changed |= grantFrontendResource("HR", "view.payroll.batch.entry");
        changed |= grantFrontendResource("HR", "view.payroll.rules");
        changed |= grantFrontendResource("HR", "view.payroll.calendar");
        changed |= grantFrontendResource("HR", "view.payroll.confirmations");

        changed |= grantFrontendResource("MANAGER", "menu.approval");
        changed |= grantFrontendResource("MANAGER", "menu.system.payroll");
        changed |= grantFrontendResource("MANAGER", "menu.payroll.batches");
        changed |= grantFrontendResource("MANAGER", "view.payroll.confirmations");
        changed |= grantFrontendResource("MANAGER", "view.payroll.batch.ledger");
        changed |= grantFrontendResource("MANAGER", "view.payroll.batch.manager");

        changed |= grantFrontendResource("EMPLOYEE", "view.employees.me");
        changed |= grantFrontendResource("EMPLOYEE", "view.payroll.confirmations");

        if (changed) {
            executeSqlSafely("UPDATE sys_user SET permission_version=COALESCE(permission_version,0)+1, update_by='frontend_route_alignment', update_time=NOW() " +
                    "WHERE status='active'");
        }
    }

    private boolean ensureFrontendResource(String type, String code, String name, String path, String component, String icon,
                                           String parentCode, int orderNum, String propsJson) {
        String codeSql = sqlLiteral(code);
        Long parentId = null;
        if (parentCode != null) {
            try {
                parentId = jdbcTemplate.queryForObject(
                        "SELECT MAX(id) FROM sys_resource WHERE code=? AND deleted=0", Long.class, parentCode);
            } catch (Exception e) {
                log.debug("Parent resource lookup failed for code={}, parentCode={}: {}", code, parentCode, e.getMessage());
            }
        }
        String parentExpression = parentId == null ? "0" : String.valueOf(parentId);
        String alignedSql = "SELECT COUNT(*) FROM sys_resource WHERE code=" + codeSql +
                " AND type=" + sqlLiteral(type) +
                " AND name=" + sqlLiteral(name) +
                " AND COALESCE(path,'')=COALESCE(" + sqlLiteral(path) + ",'')" +
                " AND COALESCE(component,'')=COALESCE(" + sqlLiteral(component) + ",'')" +
                " AND COALESCE(icon,'')=COALESCE(" + sqlLiteral(icon) + ",'')" +
                " AND COALESCE(parent_id,0)=" + parentExpression +
                " AND order_num=" + orderNum +
                " AND COALESCE(props_json,'')=COALESCE(" + sqlLiteral(propsJson) + ",'')" +
                " AND status='enabled' AND deleted=0";
        try {
            Integer aligned = jdbcTemplate.queryForObject(alignedSql, Integer.class);
            if (aligned != null && aligned > 0) {
                return false;
            }
        } catch (Exception e) {
            log.debug("Resource alignment check failed for code={}, will attempt repair: {}", code, e.getMessage());
        }

        String common = "type=" + sqlLiteral(type) +
                ", name=" + sqlLiteral(name) +
                ", path=" + sqlLiteral(path) +
                ", component=" + sqlLiteral(component) +
                ", icon=" + sqlLiteral(icon) +
                ", parent_id=" + (parentId == null ? "NULL" : String.valueOf(parentId)) +
                ", order_num=" + orderNum +
                ", props_json=" + sqlLiteral(propsJson) +
                ", status='enabled', deleted=0, update_by='frontend_route_alignment', update_time=NOW()";
        executeSqlSafely("UPDATE sys_resource SET " + common + " WHERE code=" + codeSql);
        executeSqlSafely("INSERT INTO sys_resource (type, code, name, path, component, icon, parent_id, order_num, props_json, status, create_time, update_time, create_by, update_by, deleted, version) " +
                "SELECT " + sqlLiteral(type) + ", " + codeSql + ", " + sqlLiteral(name) + ", " + sqlLiteral(path) + ", " +
                sqlLiteral(component) + ", " + sqlLiteral(icon) + ", " +
                (parentId == null ? "NULL" : String.valueOf(parentId)) + ", " +
                orderNum + ", " + sqlLiteral(propsJson) + ", 'enabled', NOW(), NOW(), 'frontend_route_alignment', 'frontend_route_alignment', 0, 0 " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code=" + codeSql + ")");
        return true;
    }

    private boolean grantAllEnabledFrontendResourcesToAdmin() {
        return executeSqlSafely("INSERT INTO sys_role_resource (role_id, resource_id, actions_json, create_time, create_by, deleted, version) " +
                "SELECT role.id, resource.id, '[\"*\"]', NOW(), 'frontend_route_alignment', 0, 0 " +
                "FROM sys_role role JOIN sys_resource resource ON resource.status='enabled' AND resource.deleted=0 " +
                "WHERE role.code='ADMIN' AND role.deleted=0 AND resource.type IN ('MENU','VIEW') " +
                "AND NOT EXISTS (SELECT 1 FROM sys_role_resource existing WHERE existing.role_id=role.id AND existing.resource_id=resource.id AND existing.deleted=0)") > 0;
    }

    private boolean grantFrontendResource(String roleCode, String resourceCode) {
        String roleSql = sqlLiteral(roleCode);
        String resourceSql = sqlLiteral(resourceCode);
        int restored = executeSqlSafely("UPDATE sys_role_resource SET deleted=0, update_by='frontend_route_alignment', update_time=NOW() " +
                "WHERE deleted=1 AND role_id IN (SELECT id FROM sys_role WHERE code=" + roleSql + " AND deleted=0) " +
                "AND resource_id IN (SELECT id FROM sys_resource WHERE code=" + resourceSql + " AND deleted=0)");
        int inserted = executeSqlSafely("INSERT INTO sys_role_resource (role_id, resource_id, actions_json, create_time, create_by, deleted, version) " +
                "SELECT role.id, resource.id, '[\"*\"]', NOW(), 'frontend_route_alignment', 0, 0 " +
                "FROM sys_role role JOIN sys_resource resource ON resource.code=" + resourceSql + " AND resource.status='enabled' AND resource.deleted=0 " +
                "WHERE role.code=" + roleSql + " AND role.deleted=0 " +
                "AND NOT EXISTS (SELECT 1 FROM sys_role_resource existing WHERE existing.role_id=role.id AND existing.resource_id=resource.id AND existing.deleted=0)");
        return restored > 0 || inserted > 0;
    }

    private String sqlLiteral(String value) {
        return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    }

    private void addColumnIfMissing(String table, String column, String ddl) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
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
            if (!indexExists(table, index)) {
                log.info("Adding index {}.{} via: {}", table, index, ddl);
                jdbcTemplate.execute(ddl);
            } else {
                log.debug("Index {}.{} already exists", table, index);
            }
        } catch (Exception e) {
            log.warn("Failed adding index {}.{}: {}", table, index, e.getMessage());
        }
    }

    private void dropIndexIfExists(String table, String index) {
        try {
            if (indexExists(table, index)) {
                jdbcTemplate.execute("ALTER TABLE `" + table + "` DROP INDEX `" + index + "`");
                log.info("Dropped obsolete index {}.{}", table, index);
            }
        } catch (Exception e) {
            log.warn("Failed dropping index {}.{}: {}", table, index, e.getMessage());
        }
    }

    private boolean indexExists(String table, String index) {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            try (ResultSet resultSet = connection.getMetaData().getIndexInfo(
                    connection.getCatalog(),
                    connection.getSchema(),
                    table,
                    false,
                    false)) {
                while (resultSet.next()) {
                    String indexName = resultSet.getString("INDEX_NAME");
                    if (index.equalsIgnoreCase(indexName)) {
                        return true;
                    }
                }
                return false;
            }
        }));
    }

    private int executeSqlSafely(String sql) {
        try {
            int affectedRows = jdbcTemplate.update(sql);
            log.info("Executed data migration SQL, affectedRows={}", affectedRows);
            return affectedRows;
        } catch (Exception e) {
            log.warn("Failed executing data migration SQL [{}]: {}", sql, e.getMessage());
            return 0;
        }
    }

    private void createTableIfMissing(String table, String ddl) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = ?",
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
