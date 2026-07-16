-- RBAC menu/page cleanup
-- Keeps the database as the source of truth while removing stale or duplicate UI resources.
-- Usage: mysql -h<host> -u<user> -p<pass> <db> < src/main/resources/sql/migrations/2026-07-13__rbac_menu_cleanup.sql

SET NAMES utf8mb4;
START TRANSACTION;
SET @now := NOW();

-- Move role grants from the legacy payroll VIEW to the canonical payroll MENU.
INSERT INTO sys_role_resource
    (role_id, resource_id, actions_json, create_time, update_time, create_by, update_by, deleted, version)
SELECT rr.role_id,
       target.id,
       rr.actions_json,
       @now,
       @now,
       'rbac_menu_cleanup',
       'rbac_menu_cleanup',
       0,
       0
FROM sys_role_resource rr
JOIN sys_resource legacy
  ON legacy.id = rr.resource_id
 AND legacy.code = 'view.payroll.batches'
JOIN sys_resource target
  ON target.code = 'menu.payroll.batches'
 AND target.status = 'enabled'
WHERE rr.deleted = 0
ON DUPLICATE KEY UPDATE
    actions_json = COALESCE(sys_role_resource.actions_json, VALUES(actions_json)),
    deleted = 0,
    update_time = VALUES(update_time),
    update_by = VALUES(update_by);

-- Keep API resources under the active UI resource groups after retiring old menu nodes.
UPDATE sys_resource child
JOIN sys_resource old_parent ON old_parent.code = 'admin.users'
JOIN sys_resource new_parent ON new_parent.code = 'admin.auth-center'
SET child.parent_id = new_parent.id,
    child.update_by = 'rbac_menu_cleanup',
    child.update_time = @now
WHERE child.parent_id = old_parent.id;

UPDATE sys_resource child
JOIN sys_resource old_parent ON old_parent.code = 'admin.resources'
JOIN sys_resource new_parent ON new_parent.code = 'admin.resources.v2'
SET child.parent_id = new_parent.id,
    child.update_by = 'rbac_menu_cleanup',
    child.update_time = @now
WHERE child.parent_id = old_parent.id;

UPDATE sys_resource child
JOIN sys_resource old_parent ON old_parent.code IN ('menu.payroll.import', 'menu.payroll.reports')
JOIN sys_resource new_parent ON new_parent.code = 'menu.system.payroll'
SET child.parent_id = new_parent.id,
    child.update_by = 'rbac_menu_cleanup',
    child.update_time = @now
WHERE child.parent_id = old_parent.id;

-- Correct component metadata for compatibility routes backed by the current auth-center pages.
UPDATE sys_resource
SET component = CASE code
                    WHEN 'admin.auth-center' THEN 'admin/auth-center/users/UserList'
                    WHEN 'admin.roles' THEN 'admin/auth-center/roles/RoleList'
                END,
    update_by = 'rbac_menu_cleanup',
    update_time = @now
WHERE code IN ('admin.auth-center', 'admin.roles')
  AND status = 'enabled';

-- Give active admin siblings deterministic, non-overlapping order numbers.
UPDATE sys_resource
SET order_num = CASE code
                    WHEN 'admin.auth-center' THEN 80
                    WHEN 'admin.user-binding' THEN 81
                    WHEN 'admin.roles' THEN 82
                    WHEN 'admin.resources.v2' THEN 83
                    WHEN 'admin.audit' THEN 84
                    WHEN 'admin.app-registry' THEN 85
                    WHEN 'admin.monitor' THEN 86
                    WHEN 'admin.tasks' THEN 87
                END,
    update_by = 'rbac_menu_cleanup',
    update_time = @now
WHERE code IN (
    'admin.auth-center',
    'admin.user-binding',
    'admin.roles',
    'admin.resources.v2',
    'admin.audit',
    'admin.app-registry',
    'admin.monitor',
    'admin.tasks'
)
AND status = 'enabled';

-- Bump permission versions before revoking stale grants so cached user bundles refresh.
UPDATE sys_user u
JOIN (
    SELECT DISTINCT ur.user_id
    FROM sys_user_role ur
    JOIN sys_role_resource rr
      ON rr.role_id = ur.role_id
     AND rr.deleted = 0
    JOIN sys_resource r ON r.id = rr.resource_id
    WHERE ur.deleted = 0
      AND r.code IN (
          'view.payroll.batches',
          'approval.workflow.detail',
          'admin.users',
          'admin.resources',
          'admin.role-auth',
          'admin.user-auth',
          'menu.payroll.import',
          'menu.payroll.reports'
      )
    UNION
    SELECT DISTINCT ur.user_id
    FROM sys_user_resource ur
    JOIN sys_resource r ON r.id = ur.resource_id
    WHERE ur.deleted = 0
      AND r.code IN (
          'view.payroll.batches',
          'approval.workflow.detail',
          'admin.users',
          'admin.resources',
          'admin.role-auth',
          'admin.user-auth',
          'menu.payroll.import',
          'menu.payroll.reports'
      )
) affected ON affected.user_id = u.id
SET u.permission_version = COALESCE(u.permission_version, 0) + 1,
    u.update_by = 'rbac_menu_cleanup',
    u.update_time = @now
WHERE u.status = 'active';

-- Retire stale/duplicate UI resources without deleting their historical records.
UPDATE sys_resource
SET status = 'disabled',
    update_by = 'rbac_menu_cleanup',
    update_time = @now
WHERE code IN (
    'view.payroll.batches',
    'approval.workflow.detail',
    'admin.users',
    'admin.resources',
    'admin.role-auth',
    'admin.user-auth',
    'menu.payroll.import',
    'menu.payroll.reports'
)
AND status = 'enabled';

UPDATE sys_role_resource rr
JOIN sys_resource r ON r.id = rr.resource_id
SET rr.deleted = 1,
    rr.update_by = 'rbac_menu_cleanup',
    rr.update_time = @now
WHERE rr.deleted = 0
  AND r.status = 'disabled'
  AND r.code IN (
      'view.payroll.batches',
      'approval.workflow.detail',
      'admin.users',
      'admin.resources',
      'admin.role-auth',
      'admin.user-auth',
      'menu.payroll.import',
      'menu.payroll.reports'
  );

UPDATE sys_user_resource ur
JOIN sys_resource r ON r.id = ur.resource_id
SET ur.deleted = 1,
    ur.update_by = 'rbac_menu_cleanup',
    ur.update_time = @now
WHERE ur.deleted = 0
  AND r.status = 'disabled'
  AND r.code IN (
      'view.payroll.batches',
      'approval.workflow.detail',
      'admin.users',
      'admin.resources',
      'admin.role-auth',
      'admin.user-auth',
      'menu.payroll.import',
      'menu.payroll.reports'
  );

COMMIT;
