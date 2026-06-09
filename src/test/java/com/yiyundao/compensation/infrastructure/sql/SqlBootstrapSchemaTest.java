package com.yiyundao.compensation.infrastructure.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SqlBootstrapSchemaTest {

    private static final List<String> REQUIRED_SYS_USER_ROLE_COLUMNS = List.of(
            "`id` bigint NOT NULL AUTO_INCREMENT",
            "`granted_by` bigint",
            "`granted_at` datetime",
            "`expires_at` datetime",
            "`remarks` varchar(500)",
            "`delete_by` varchar(50)",
            "`delete_time` datetime",
            "`update_time` datetime",
            "`create_by` varchar(50)",
            "`update_by` varchar(50)",
            "`deleted` tinyint(1)",
            "`version` int"
    );

    private static final List<String> REQUIRED_SYS_ROLE_COLUMNS = List.of(
            "`role_type` varchar(20)",
            "`sort_order` int",
            "`is_editable` tinyint(1)",
            "`icon` varchar(100)",
            "`remarks` varchar(500)"
    );

    @Test
    void schemaSqlShouldBootstrapRbacTablesRequiredByRuntimeEntities() throws IOException {
        String sql = readSql("src/main/resources/sql/schema.sql");

        assertThat(sql).contains(
                "CREATE TABLE `sys_resource`",
                "CREATE TABLE `sys_role`",
                "CREATE TABLE `sys_role_resource`",
                "CREATE TABLE `sys_user_role`",
                "CREATE TABLE `sys_user_resource`",
                "CREATE TABLE `resource_snapshot`",
                "`permission_version` int DEFAULT '0'"
        );
        assertThat(sql).contains(REQUIRED_SYS_USER_ROLE_COLUMNS.toArray(String[]::new));
        assertThat(sql).contains(REQUIRED_SYS_ROLE_COLUMNS.toArray(String[]::new));
        assertThat(sql).contains(
                "INSERT INTO `sys_role`",
                "('ADMIN', '系统管理员'",
                "INSERT INTO `sys_user_role`",
                "JOIN `sys_role` r ON r.code = 'ADMIN'"
        );
    }

    @Test
    void initCleanSqlShouldKeepSysUserRoleAlignedWithBaseEntity() throws IOException {
        String sql = readSql("src/main/resources/sql/init_clean.sql");

        assertThat(sql).contains(REQUIRED_SYS_USER_ROLE_COLUMNS.toArray(String[]::new));
        assertThat(sql).contains(REQUIRED_SYS_ROLE_COLUMNS.toArray(String[]::new));
        assertThat(sql).contains(
                "`permission_version` int DEFAULT '0'",
                "INSERT INTO `sys_role`",
                "('ADMIN', '系统管理员'",
                "INSERT INTO `sys_user_role`",
                "JOIN `sys_role` r ON r.code = 'ADMIN'"
        );
    }

    @Test
    void normalMigrationDirectoryShouldNotContainRollbackScripts() throws IOException {
        try (Stream<Path> migrations = Files.list(Path.of("src/main/resources/sql/migrations"))) {
            assertThat(migrations)
                    .noneMatch(path -> path.getFileName().toString().toLowerCase().contains("rollback"));
        }

        assertThat(Path.of("src/main/resources/sql/rollback/2026-03-01__settlement_provider_enhancement_rollback.sql"))
                .exists();
    }

    private String readSql(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
