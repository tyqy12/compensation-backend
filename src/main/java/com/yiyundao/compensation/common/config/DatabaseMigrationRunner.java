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
        log.info("Running audit_log migration (if needed)...");
        try {
            jdbcTemplate.execute(
                "ALTER TABLE `audit_log` " +
                "ADD COLUMN IF NOT EXISTS `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`"
            );
        } catch (Exception e) {
            log.warn("Skipping add update_time: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute(
                "ALTER TABLE `audit_log` " +
                "ADD COLUMN IF NOT EXISTS `update_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人' AFTER `create_by`"
            );
        } catch (Exception e) {
            log.warn("Skipping add update_by: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute(
                "ALTER TABLE `audit_log` " +
                "ADD COLUMN IF NOT EXISTS `deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)' AFTER `update_by`"
            );
        } catch (Exception e) {
            log.warn("Skipping add deleted: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute(
                "ALTER TABLE `audit_log` " +
                "ADD COLUMN IF NOT EXISTS `version` INT DEFAULT '0' COMMENT '乐观锁版本号' AFTER `deleted`"
            );
        } catch (Exception e) {
            log.warn("Skipping add version: {}", e.getMessage());
        }

        log.info("audit_log migration finished.");
    }
}

