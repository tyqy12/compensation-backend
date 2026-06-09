package com.yiyundao.compensation.infrastructure.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql(statements = {
        "DROP TABLE IF EXISTS sys_user_role",
        "CREATE TABLE sys_user_role (id BIGINT NOT NULL PRIMARY KEY, user_id BIGINT NOT NULL, " +
                "role_id BIGINT NOT NULL, granted_by BIGINT DEFAULT NULL, granted_at DATETIME DEFAULT NULL, " +
                "expires_at DATETIME DEFAULT NULL, remarks VARCHAR(500) DEFAULT NULL, " +
                "delete_by VARCHAR(50) DEFAULT NULL, delete_time DATETIME DEFAULT NULL, " +
                "create_time DATETIME DEFAULT NULL, update_time DATETIME DEFAULT NULL, " +
                "create_by VARCHAR(50) DEFAULT NULL, update_by VARCHAR(50) DEFAULT NULL, " +
                "deleted TINYINT DEFAULT 0, version INT DEFAULT 0, " +
                "CONSTRAINT uk_test_user_role UNIQUE (user_id, role_id))",
        "INSERT INTO sys_user_role (id, user_id, role_id, deleted, delete_by, delete_time, create_time) " +
                "VALUES (1, 10, 20, 1, '88', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
})
@Sql(statements = "DROP TABLE IF EXISTS sys_user_role", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class SysUserRoleMapperTest {

    @Autowired
    private SysUserRoleMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void restoreDeletedRoleShouldUpdateLogicallyDeletedRow() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);

        int affected = mapper.restoreDeletedRole(10L, 20L, 99L, now, null, "restore", "99", now);

        assertThat(affected).isEqualTo(1);
        Integer restoredRows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_user_role
                WHERE user_id = 10
                  AND role_id = 20
                  AND deleted = 0
                  AND granted_by = 99
                  AND remarks = 'restore'
                  AND update_by = '99'
                  AND delete_by IS NULL
                  AND delete_time IS NULL
                """, Integer.class);
        assertThat(restoredRows).isEqualTo(1);
    }

    @Test
    void restoreDeletedRoleShouldIgnoreActiveRow() {
        jdbcTemplate.update("UPDATE sys_user_role SET deleted = 0 WHERE user_id = 10 AND role_id = 20");

        int affected = mapper.restoreDeletedRole(
                10L, 20L, 99L, LocalDateTime.now(), null, "restore", "99", LocalDateTime.now());

        assertThat(affected).isZero();
    }
}
