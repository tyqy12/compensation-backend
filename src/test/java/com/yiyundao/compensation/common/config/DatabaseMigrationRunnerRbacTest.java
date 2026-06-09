package com.yiyundao.compensation.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DatabaseMigrationRunnerRbacTest {

    private static final List<String> SYS_USER_ROLE_COLUMNS = List.of(
            "id",
            "granted_by",
            "granted_at",
            "expires_at",
            "remarks",
            "delete_by",
            "delete_time",
            "create_time",
            "update_time",
            "create_by",
            "update_by",
            "deleted",
            "version"
    );

    private static final List<String> BASE_ENTITY_COLUMNS = List.of(
            "create_time",
            "update_time",
            "create_by",
            "update_by",
            "deleted",
            "version"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void runShouldUpgradeLegacyRbacTablesAndMigrateAdminRoleAssignments() {
        recreateLegacyRbacTables();
        jdbcTemplate.update("""
                UPDATE sys_user
                SET roles = 'ADMIN, ROLE_FINANCE',
                    permission_version = NULL
                WHERE username = 'admin'
                """);

        new DatabaseMigrationRunner(jdbcTemplate).run(null);

        assertThat(columnsOf("sys_user_role")).containsAll(SYS_USER_ROLE_COLUMNS);
        assertThat(columnsOf("sys_role_resource")).containsAll(BASE_ENTITY_COLUMNS);
        assertThat(columnsOf("sys_user_resource")).containsAll(BASE_ENTITY_COLUMNS);
        assertThat(columnsOf("sys_role")).contains(
                "role_type",
                "sort_order",
                "is_editable",
                "icon",
                "remarks"
        );

        Integer permissionVersion = jdbcTemplate.queryForObject(
                "SELECT permission_version FROM sys_user WHERE username = 'admin'",
                Integer.class);
        assertThat(permissionVersion).isZero();

        Integer adminRoles = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
                JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
                WHERE u.username = 'admin'
                  AND r.code IN ('ADMIN', 'FINANCE')
                """, Integer.class);
        assertThat(adminRoles).isEqualTo(2);

        Integer seededRoles = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_role
                WHERE code IN ('ADMIN', 'MANAGER', 'FINANCE', 'HR', 'EMPLOYEE')
                  AND deleted = 0
                """, Integer.class);
        assertThat(seededRoles).isEqualTo(5);
    }

    private void recreateLegacyRbacTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS sys_user_resource");
        jdbcTemplate.execute("DROP TABLE IF EXISTS sys_role_resource");
        jdbcTemplate.execute("DROP TABLE IF EXISTS sys_user_role");
        jdbcTemplate.execute("DROP TABLE IF EXISTS resource_snapshot");
        jdbcTemplate.execute("DROP TABLE IF EXISTS sys_role");
        jdbcTemplate.execute("DROP TABLE IF EXISTS sys_resource");

        jdbcTemplate.execute("""
                CREATE TABLE sys_resource (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  type VARCHAR(20) NOT NULL,
                  code VARCHAR(100) NOT NULL,
                  name VARCHAR(100) NOT NULL,
                  path VARCHAR(255) DEFAULT NULL,
                  component VARCHAR(255) DEFAULT NULL,
                  icon VARCHAR(100) DEFAULT NULL,
                  parent_id BIGINT DEFAULT NULL,
                  order_num INT DEFAULT 0,
                  props_json JSON DEFAULT NULL,
                  status VARCHAR(20) DEFAULT 'enabled',
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  create_by VARCHAR(50) DEFAULT NULL,
                  update_by VARCHAR(50) DEFAULT NULL,
                  deleted TINYINT DEFAULT 0,
                  version INT DEFAULT 0,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_resource_code (code)
                )
                """);
        jdbcTemplate.execute("""
                INSERT INTO sys_resource (type, code, name, status)
                VALUES ('API', 'api.legacy.admin', '旧版管理员接口', 'enabled')
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sys_role (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  code VARCHAR(50) NOT NULL,
                  name VARCHAR(100) NOT NULL,
                  description VARCHAR(255) DEFAULT NULL,
                  status VARCHAR(20) DEFAULT 'enabled',
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  create_by VARCHAR(50) DEFAULT NULL,
                  update_by VARCHAR(50) DEFAULT NULL,
                  deleted TINYINT DEFAULT 0,
                  version INT DEFAULT 0,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_role_code (code)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sys_role_resource (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  role_id BIGINT NOT NULL,
                  resource_id BIGINT NOT NULL,
                  actions_json JSON DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_role_resource (role_id, resource_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sys_user_role (
                  user_id BIGINT NOT NULL,
                  role_id BIGINT NOT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_user_role (user_id, role_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sys_user_resource (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  resource_id BIGINT NOT NULL,
                  actions_json JSON DEFAULT NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_user_resource (user_id, resource_id)
                )
                """);
    }

    private List<String> columnsOf(String tableName) {
        return jdbcTemplate.queryForList("""
                        SELECT COLUMN_NAME
                        FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_SCHEMA = SCHEMA()
                          AND TABLE_NAME = ?
                        """,
                String.class,
                tableName);
    }
}
