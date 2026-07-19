-- Retire role.* aliases from the legacy RBAC bootstrap.
-- Users are moved to the canonical role before the legacy assignment is hidden.
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_user_role
    (user_id, role_id, granted_by, granted_at, expires_at, remarks,
     create_time, update_time, create_by, update_by, deleted, version)
SELECT legacy_assignment.user_id, canonical_role.id, legacy_assignment.granted_by,
       legacy_assignment.granted_at, legacy_assignment.expires_at,
       CONCAT(COALESCE(legacy_assignment.remarks, ''), ' [legacy-role-migrated]'),
       NOW(), NOW(), 'legacy_role_cleanup', 'legacy_role_cleanup', 0, 0
FROM sys_user_role legacy_assignment
JOIN sys_role legacy_role ON legacy_role.id=legacy_assignment.role_id AND legacy_role.deleted=0
JOIN sys_role canonical_role ON canonical_role.code=CASE legacy_role.code
    WHEN 'role.admin.all' THEN 'ADMIN'
    WHEN 'role.finance' THEN 'FINANCE'
    WHEN 'role.hr' THEN 'HR'
    WHEN 'role.manager' THEN 'MANAGER'
    WHEN 'role.employee' THEN 'EMPLOYEE'
END AND canonical_role.deleted=0 AND canonical_role.status='enabled'
WHERE legacy_assignment.deleted=0
  AND legacy_role.code IN ('role.admin.all','role.finance','role.hr','role.manager','role.employee')
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role existing
      WHERE existing.user_id=legacy_assignment.user_id
        AND existing.role_id=canonical_role.id
        AND existing.deleted=0
  );

UPDATE sys_user_role legacy_assignment
JOIN sys_role legacy_role ON legacy_role.id=legacy_assignment.role_id
SET legacy_assignment.deleted=1, legacy_assignment.delete_by='legacy_role_cleanup',
    legacy_assignment.delete_time=NOW(), legacy_assignment.update_by='legacy_role_cleanup',
    legacy_assignment.update_time=NOW()
WHERE legacy_assignment.deleted=0
  AND legacy_role.code IN ('role.admin.all','role.finance','role.hr','role.manager','role.employee');

UPDATE sys_role_resource legacy_permission
JOIN sys_role legacy_role ON legacy_role.id=legacy_permission.role_id
SET legacy_permission.deleted=1, legacy_permission.update_by='legacy_role_cleanup',
    legacy_permission.update_time=NOW()
WHERE legacy_permission.deleted=0
  AND legacy_role.code IN ('role.admin.all','role.finance','role.hr','role.manager','role.employee');

UPDATE sys_role_permission legacy_permission
JOIN sys_role legacy_role ON legacy_role.id=legacy_permission.role_id
SET legacy_permission.deleted=1, legacy_permission.status='disabled',
    legacy_permission.update_by='legacy_role_cleanup', legacy_permission.update_time=NOW()
WHERE legacy_permission.deleted=0
  AND legacy_role.code IN ('role.admin.all','role.finance','role.hr','role.manager','role.employee');

UPDATE sys_role
SET status='disabled', deleted=1, update_by='legacy_role_cleanup', update_time=NOW()
WHERE code IN ('role.admin.all','role.finance','role.hr','role.manager','role.employee')
  AND deleted=0;

UPDATE sys_user
SET permission_version=COALESCE(permission_version,0)+1,
    update_by='legacy_role_cleanup', update_time=NOW()
WHERE status='active'
  AND id IN (
      SELECT user_id FROM sys_user_role
      WHERE create_by='legacy_role_cleanup' OR update_by='legacy_role_cleanup'
  );

INSERT INTO sys_config
    (config_key, config_value, config_type, config_desc, create_time, update_time, create_by, update_by, deleted, version)
SELECT 'rbac.legacy.roles.retired', 'true', 'boolean', '旧版 role.* 角色已迁移并隐藏', NOW(), NOW(),
       'legacy_role_cleanup', 'legacy_role_cleanup', 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key='rbac.legacy.roles.retired' AND deleted=0
);

COMMIT;
