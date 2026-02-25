-- ============================================================================
-- 角色数据迁移脚本
-- 功能：将 sys_user.roles 字段数据同步到 sys_user_role 关联表
-- 执行时间：2026-01-28
-- ============================================================================

-- 检查迁移是否需要执行（关联表为空且用户表有角色数据）
SELECT
    '检查迁移条件...' AS status,
    (SELECT COUNT(*) FROM sys_user WHERE roles IS NOT NULL AND roles != '') AS users_with_roles,
    (SELECT COUNT(*) FROM sys_user_role WHERE deleted = 0) AS existing_user_roles;

-- 如果关联表已有数据，建议跳过迁移
-- ============================================================================
-- 第一步：为现有用户创建角色关联记录
-- ============================================================================

-- 插入 ADMIN 角色关联
INSERT INTO sys_user_role (user_id, role_id, granted_by, granted_at, deleted, create_time, create_by)
SELECT
    u.id AS user_id,
    r.id AS role_id,
    1 AS granted_by,  -- 系统管理员
    NOW() AS granted_at,
    0 AS deleted,
    NOW() AS create_time,
    'system_migration' AS create_by
FROM sys_user u
CROSS JOIN sys_role r
WHERE r.code = 'ADMIN'
  AND r.status = 'enabled'
  AND u.roles IS NOT NULL
  AND u.roles LIKE '%ROLE_ADMIN%'
  AND u.id NOT IN (
      SELECT user_id FROM sys_user_role WHERE role_id = r.id AND deleted = 0
  );

-- 插入 MANAGER 角色关联
INSERT INTO sys_user_role (user_id, role_id, granted_by, granted_at, deleted, create_time, create_by)
SELECT
    u.id AS user_id,
    r.id AS role_id,
    1 AS granted_by,
    NOW() AS granted_at,
    0 AS deleted,
    NOW() AS create_time,
    'system_migration' AS create_by
FROM sys_user u
CROSS JOIN sys_role r
WHERE r.code = 'MANAGER'
  AND r.status = 'enabled'
  AND u.roles IS NOT NULL
  AND u.roles LIKE '%ROLE_MANAGER%'
  AND u.id NOT IN (
      SELECT user_id FROM sys_user_role WHERE role_id = r.id AND deleted = 0
  );

-- 插入 FINANCE 角色关联
INSERT INTO sys_user_role (user_id, role_id, granted_by, granted_at, deleted, create_time, create_by)
SELECT
    u.id AS user_id,
    r.id AS role_id,
    1 AS granted_by,
    NOW() AS granted_at,
    0 AS deleted,
    NOW() AS create_time,
    'system_migration' AS create_by
FROM sys_user u
CROSS JOIN sys_role r
WHERE r.code = 'FINANCE'
  AND r.status = 'enabled'
  AND u.roles IS NOT NULL
  AND u.roles LIKE '%ROLE_FINANCE%'
  AND u.id NOT IN (
      SELECT user_id FROM sys_user_role WHERE role_id = r.id AND deleted = 0
  );

-- 插入 EMPLOYEE 角色关联
INSERT INTO sys_user_role (user_id, role_id, granted_by, granted_at, deleted, create_time, create_by)
SELECT
    u.id AS user_id,
    r.id AS role_id,
    1 AS granted_by,
    NOW() AS granted_at,
    0 AS deleted,
    NOW() AS create_time,
    'system_migration' AS create_by
FROM sys_user u
CROSS JOIN sys_role r
WHERE r.code = 'EMPLOYEE'
  AND r.status = 'enabled'
  AND u.roles IS NOT NULL
  AND u.roles LIKE '%ROLE_EMPLOYEE%'
  AND u.id NOT IN (
      SELECT user_id FROM sys_user_role WHERE role_id = r.id AND deleted = 0
  );

-- 插入 USER 角色关联（默认角色）
INSERT INTO sys_user_role (user_id, role_id, granted_by, granted_at, deleted, create_time, create_by)
SELECT
    u.id AS user_id,
    r.id AS role_id,
    1 AS granted_by,
    NOW() AS granted_at,
    0 AS deleted,
    NOW() AS create_time,
    'system_migration' AS create_by
FROM sys_user u
CROSS JOIN sys_role r
WHERE r.code = 'USER'
  AND r.status = 'enabled'
  AND u.roles IS NOT NULL
  AND u.roles LIKE '%ROLE_USER%'
  AND u.id NOT IN (
      SELECT user_id FROM sys_user_role WHERE role_id = r.id AND deleted = 0
  );

-- ============================================================================
-- 第二步：验证迁移结果
-- ============================================================================

-- 统计迁移情况
SELECT
    '迁移统计' AS report_type,
    (SELECT COUNT(*) FROM sys_user WHERE roles IS NOT NULL AND roles != '') AS users_with_roles,
    (SELECT COUNT(*) FROM sys_user_role WHERE deleted = 0) AS total_user_role_records,
    (SELECT COUNT(DISTINCT user_id) FROM sys_user_role WHERE deleted = 0) AS users_with_role_records;

-- 检查哪些用户的角色数据未完全迁移
SELECT
    u.id,
    u.username,
    u.roles AS old_roles,
    GROUP_CONCAT(DISTINCT r.code ORDER BY r.code SEPARATOR ', ') AS new_roles
FROM sys_user u
LEFT JOIN sys_user_role ur ON u.id = ur.user_id AND ur.deleted = 0
LEFT JOIN sys_role r ON ur.role_id = r.id
WHERE u.roles IS NOT NULL AND u.roles != ''
GROUP BY u.id, u.username, u.roles
HAVING old_roles != new_roles OR new_roles IS NULL;

-- ============================================================================
-- 第三步：可选 - 清空旧的 roles 字段
-- 注意：此步骤需要业务确认后再执行，建议在完全验证后执行
--
-- UPDATE sys_user SET roles = NULL WHERE roles IS NOT NULL AND roles != '';
-- ============================================================================

-- 迁移完成提示
SELECT '角色数据迁移完成！建议执行以下验证：' AS message;
SELECT '1. 检查用户登录后角色是否正确加载' AS validation_step;
SELECT '2. 检查管理员权限是否正常' AS validation_step;
SELECT '3. 检查业务功能是否正常工作' AS validation_step;
