-- Database-driven RBAC v3 repair.
-- Fix multi-level payroll compliance routes and backfill legacy active grants into
-- the runtime role/user permission tables without overwriting explicit DENY rows.

SET NAMES utf8mb4;
START TRANSACTION;

UPDATE sys_resource
SET path='/api/payroll/compliance/**',
    update_by='rbac_migration_v3',
    update_time=NOW()
WHERE code='api.payroll.compliance.tax'
  AND path='/api/payroll/compliance/*'
  AND deleted=0;

UPDATE sys_role_permission rp
JOIN sys_role_resource legacy
  ON legacy.role_id=rp.role_id
 AND legacy.resource_id=rp.resource_id
 AND legacy.deleted=0
JOIN sys_resource_action ra
  ON ra.resource_id=rp.resource_id
 AND ra.action_id=rp.action_id
 AND ra.status='enabled'
 AND ra.deleted=0
JOIN sys_permission_action action
  ON action.id=rp.action_id
 AND action.status='enabled'
 AND action.deleted=0
SET rp.status='enabled', rp.deleted=0, rp.update_by='rbac_migration_v3', rp.update_time=NOW()
WHERE rp.effect <> 'DENY';

INSERT INTO sys_role_permission
    (role_id,resource_id,action_id,effect,scope_json,status,create_time,update_time,create_by,update_by,deleted,version)
SELECT legacy.role_id, legacy.resource_id, ra.action_id, 'ALLOW', '{"mode":"ALL"}', 'enabled', NOW(), NOW(),
       'rbac_migration_v3', 'rbac_migration_v3', 0, 0
FROM sys_role_resource legacy
JOIN sys_resource_action ra
  ON ra.resource_id=legacy.resource_id
 AND ra.status='enabled'
 AND ra.deleted=0
JOIN sys_permission_action action
  ON action.id=ra.action_id
 AND action.status='enabled'
 AND action.deleted=0
WHERE legacy.deleted=0
  AND (legacy.actions_json IS NULL
       OR JSON_CONTAINS(legacy.actions_json, JSON_QUOTE('*'))
       OR JSON_CONTAINS(legacy.actions_json, JSON_QUOTE(action.code)))
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission existing
      WHERE existing.role_id=legacy.role_id
        AND existing.resource_id=legacy.resource_id
        AND existing.action_id=ra.action_id
  );

UPDATE sys_user_permission up
JOIN sys_user_resource legacy
  ON legacy.user_id=up.user_id
 AND legacy.resource_id=up.resource_id
 AND legacy.deleted=0
JOIN sys_resource_action ra
  ON ra.resource_id=up.resource_id
 AND ra.action_id=up.action_id
 AND ra.status='enabled'
 AND ra.deleted=0
JOIN sys_permission_action action
  ON action.id=up.action_id
 AND action.status='enabled'
 AND action.deleted=0
SET up.status='enabled', up.deleted=0, up.update_by='rbac_migration_v3', up.update_time=NOW()
WHERE up.effect <> 'DENY';

INSERT INTO sys_user_permission
    (user_id,resource_id,action_id,effect,scope_json,status,create_time,update_time,create_by,update_by,deleted,version)
SELECT legacy.user_id, legacy.resource_id, ra.action_id, 'ALLOW', '{"mode":"ALL"}', 'enabled', NOW(), NOW(),
       'rbac_migration_v3', 'rbac_migration_v3', 0, 0
FROM sys_user_resource legacy
JOIN sys_resource_action ra
  ON ra.resource_id=legacy.resource_id
 AND ra.status='enabled'
 AND ra.deleted=0
JOIN sys_permission_action action
  ON action.id=ra.action_id
 AND action.status='enabled'
 AND action.deleted=0
WHERE legacy.deleted=0
  AND (legacy.actions_json IS NULL
       OR JSON_CONTAINS(legacy.actions_json, JSON_QUOTE('*'))
       OR JSON_CONTAINS(legacy.actions_json, JSON_QUOTE(action.code)))
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_permission existing
      WHERE existing.user_id=legacy.user_id
        AND existing.resource_id=legacy.resource_id
        AND existing.action_id=ra.action_id
  );

UPDATE sys_user u
SET permission_version=COALESCE(permission_version,0)+1,
    update_by='rbac_migration_v3',
    update_time=NOW()
WHERE status='active';

INSERT INTO sys_config
    (config_key,config_value,config_type,config_desc,create_time,update_time,create_by,update_by,deleted,version)
SELECT 'rbac.database.permission.migrated.v3','true','boolean',
       'Database-driven RBAC v3 route and legacy grant repair',NOW(),NOW(),
       'rbac_migration_v3','rbac_migration_v3',0,0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config
    WHERE config_key='rbac.database.permission.migrated.v3' AND deleted=0
);

COMMIT;
