-- Database-driven RBAC v2 repair.
-- This migration is idempotent and repairs installations where the original
-- RBAC migration created /auth/me/** but did not grant its read operation.

SET NAMES utf8mb4;

SET @add_access_mode_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE sys_resource ADD COLUMN access_mode varchar(20) NOT NULL DEFAULT ''USER'' COMMENT ''Access mode: PUBLIC/USER/EXTERNAL''',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_resource'
      AND COLUMN_NAME = 'access_mode'
);
PREPARE add_access_mode_stmt FROM @add_access_mode_sql;
EXECUTE add_access_mode_stmt;
DEALLOCATE PREPARE add_access_mode_stmt;

CREATE TABLE IF NOT EXISTS sys_permission_action (
    id bigint NOT NULL AUTO_INCREMENT,
    code varchar(100) NOT NULL,
    name varchar(100) NOT NULL,
    description varchar(255) DEFAULT NULL,
    http_methods varchar(100) DEFAULT NULL,
    authority varchar(150) DEFAULT NULL,
    status varchar(20) NOT NULL DEFAULT 'enabled',
    order_num int NOT NULL DEFAULT 0,
    props_json text DEFAULT NULL,
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by varchar(50) DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    deleted tinyint(1) NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_action_code (code),
    KEY idx_permission_action_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Permission actions';

CREATE TABLE IF NOT EXISTS sys_resource_action (
    id bigint NOT NULL AUTO_INCREMENT,
    resource_id bigint NOT NULL,
    action_id bigint NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'enabled',
    props_json text DEFAULT NULL,
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by varchar(50) DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    deleted tinyint(1) NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_action (resource_id, action_id),
    KEY idx_resource_action_resource (resource_id, status, deleted),
    KEY idx_resource_action_action (action_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Resource actions';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id bigint NOT NULL AUTO_INCREMENT,
    role_id bigint NOT NULL,
    resource_id bigint NOT NULL,
    action_id bigint NOT NULL,
    effect varchar(10) NOT NULL DEFAULT 'ALLOW',
    scope_json text DEFAULT NULL,
    condition_json text DEFAULT NULL,
    status varchar(20) NOT NULL DEFAULT 'enabled',
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by varchar(50) DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    deleted tinyint(1) NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, resource_id, action_id),
    KEY idx_role_permission_subject (role_id, status, deleted),
    KEY idx_role_permission_resource (resource_id, action_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Role resource action permissions';

CREATE TABLE IF NOT EXISTS sys_user_permission (
    id bigint NOT NULL AUTO_INCREMENT,
    user_id bigint NOT NULL,
    resource_id bigint NOT NULL,
    action_id bigint NOT NULL,
    effect varchar(10) NOT NULL DEFAULT 'ALLOW',
    scope_json text DEFAULT NULL,
    condition_json text DEFAULT NULL,
    status varchar(20) NOT NULL DEFAULT 'enabled',
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by varchar(50) DEFAULT NULL,
    update_by varchar(50) DEFAULT NULL,
    deleted tinyint(1) NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_permission (user_id, resource_id, action_id),
    KEY idx_user_permission_subject (user_id, status, deleted),
    KEY idx_user_permission_resource (resource_id, action_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User resource action permissions';

INSERT INTO sys_permission_action
    (code, name, description, http_methods, status, order_num, create_time, update_time, create_by, update_by, deleted, version)
SELECT 'read', 'Read', 'Read current user permissions', 'GET,HEAD', 'enabled', 10,
       NOW(), NOW(), 'rbac_migration_v2', 'rbac_migration_v2', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_action WHERE code = 'read');

INSERT INTO sys_resource
    (type, code, name, path, access_mode, status, create_time, update_time, create_by, update_by, deleted, version)
SELECT 'API', 'rbac.user.auth.me', 'Current user permissions', '/auth/me/**', 'USER', 'enabled',
       NOW(), NOW(), 'rbac_migration_v2', 'rbac_migration_v2', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_resource WHERE code = 'rbac.user.auth.me');

UPDATE sys_resource_action ra
JOIN sys_resource r ON r.id = ra.resource_id AND r.code = 'rbac.user.auth.me' AND r.deleted = 0
JOIN sys_permission_action a ON a.id = ra.action_id AND a.code = 'read' AND a.deleted = 0
SET ra.status = 'enabled', ra.deleted = 0, ra.update_by = 'rbac_migration_v2', ra.update_time = NOW();

INSERT INTO sys_resource_action
    (resource_id, action_id, status, create_time, update_time, create_by, update_by, deleted, version)
SELECT r.id, a.id, 'enabled', NOW(), NOW(), 'rbac_migration_v2', 'rbac_migration_v2', 0, 0
FROM sys_resource r
JOIN sys_permission_action a ON a.code = 'read' AND a.status = 'enabled' AND a.deleted = 0
WHERE r.code = 'rbac.user.auth.me' AND r.status = 'enabled' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_resource_action existing
      WHERE existing.resource_id = r.id AND existing.action_id = a.id
  );

UPDATE sys_role_permission rp
JOIN sys_role role ON role.id = rp.role_id AND role.status = 'enabled' AND role.deleted = 0
JOIN sys_resource resource ON resource.id = rp.resource_id AND resource.code = 'rbac.user.auth.me'
JOIN sys_permission_action action ON action.id = rp.action_id AND action.code = 'read'
SET rp.status = 'enabled', rp.deleted = 0, rp.update_by = 'rbac_migration_v2', rp.update_time = NOW()
WHERE rp.effect = 'ALLOW';

INSERT INTO sys_role_permission
    (role_id, resource_id, action_id, effect, scope_json, status, create_time, update_time,
     create_by, update_by, deleted, version)
SELECT role.id, resource.id, action.id, 'ALLOW', '{"mode":"ALL"}', 'enabled', NOW(), NOW(),
       'rbac_migration_v2', 'rbac_migration_v2', 0, 0
FROM sys_role role
JOIN sys_resource resource ON resource.code = 'rbac.user.auth.me'
    AND resource.status = 'enabled' AND resource.deleted = 0
JOIN sys_permission_action action ON action.code = 'read'
    AND action.status = 'enabled' AND action.deleted = 0
WHERE role.status = 'enabled' AND role.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.role_id = role.id
        AND existing.resource_id = resource.id
        AND existing.action_id = action.id
  );

UPDATE sys_config
SET config_value = 'true', config_type = 'boolean',
    config_desc = 'Database-driven RBAC v2 repair completed',
    update_by = 'rbac_migration_v2', update_time = NOW()
WHERE config_key = 'rbac.database.permission.migrated.v2' AND deleted = 0;

INSERT INTO sys_config
    (config_key, config_value, config_type, config_desc, create_time, update_time,
     create_by, update_by, deleted, version)
SELECT 'rbac.database.permission.migrated.v2', 'true', 'boolean',
       'Database-driven RBAC v2 repair completed', NOW(), NOW(),
       'rbac_migration_v2', 'rbac_migration_v2', 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_key = 'rbac.database.permission.migrated.v2' AND deleted = 0
);
