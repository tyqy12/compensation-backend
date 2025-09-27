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
        addColumnIfMissing("audit_log", "update_time",
                "ALTER TABLE audit_log ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`");
        addColumnIfMissing("audit_log", "update_by",
                "ALTER TABLE audit_log ADD COLUMN `update_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人' AFTER `create_by`");
        addColumnIfMissing("audit_log", "deleted",
                "ALTER TABLE audit_log ADD COLUMN `deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除(0:未删除,1:已删除)' AFTER `update_by`");
        addColumnIfMissing("audit_log", "version",
                "ALTER TABLE audit_log ADD COLUMN `version` INT DEFAULT '0' COMMENT '乐观锁版本号' AFTER `deleted`");
        log.info("audit_log migration finished.");
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
}
