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
        createTableIfMissing("sys_config",
                "CREATE TABLE `sys_config` (\n" +
                "  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',\n" +
                "  `config_key` varchar(100) NOT NULL COMMENT '配置键',\n" +
                "  `config_value` text COMMENT '配置值',\n" +
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

        // 2) audit_log incremental columns
        addColumnIfMissing("audit_log", "update_time",
                "ALTER TABLE audit_log ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`");
        addColumnIfMissing("audit_log", "update_by",
                "ALTER TABLE audit_log ADD COLUMN `update_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人' AFTER `create_by`");
        addColumnIfMissing("audit_log", "deleted",
                "ALTER TABLE audit_log ADD COLUMN `deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)' AFTER `update_by`");
        addColumnIfMissing("audit_log", "version",
                "ALTER TABLE audit_log ADD COLUMN `version` INT DEFAULT '0' COMMENT '乐观锁版本号' AFTER `deleted`");
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
