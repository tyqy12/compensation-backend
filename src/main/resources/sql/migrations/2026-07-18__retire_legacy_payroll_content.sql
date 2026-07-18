-- Retire legacy payroll UI resources after the new operations/rules/calendar workspaces went live.
-- Historical payroll data is retained; only stale menu components and grants are disabled.

SET NAMES utf8mb4;
START TRANSACTION;

UPDATE sys_resource
SET name = '薪酬运营',
    component = 'payroll/Operations',
    update_by = 'legacy_payroll_retirement',
    update_time = NOW()
WHERE code = 'menu.payroll.batches'
  AND deleted = 0
  AND (component IS NULL OR component <> 'payroll/Operations');

UPDATE sys_resource
SET status = 'disabled',
    update_by = 'legacy_payroll_retirement',
    update_time = NOW()
WHERE deleted = 0
  AND status <> 'disabled'
  AND code IN ('view.payroll.batches', 'menu.payroll.import', 'menu.payroll.reports');

UPDATE sys_user u
JOIN (
    SELECT DISTINCT ur.user_id
    FROM sys_user_role ur
    JOIN sys_role_resource rr ON rr.role_id = ur.role_id AND rr.deleted = 0
    JOIN sys_resource r ON r.id = rr.resource_id
    WHERE ur.deleted = 0
      AND r.code IN ('view.payroll.batches', 'menu.payroll.import', 'menu.payroll.reports')
    UNION
    SELECT DISTINCT ur.user_id
    FROM sys_user_resource ur
    JOIN sys_resource r ON r.id = ur.resource_id
    WHERE ur.deleted = 0
      AND r.code IN ('view.payroll.batches', 'menu.payroll.import', 'menu.payroll.reports')
) affected ON affected.user_id = u.id
SET u.permission_version = COALESCE(u.permission_version, 0) + 1,
    u.update_by = 'legacy_payroll_retirement',
    u.update_time = NOW()
WHERE u.status = 'active';

UPDATE sys_role_resource rr
JOIN sys_resource r ON r.id = rr.resource_id
SET rr.deleted = 1,
    rr.update_by = 'legacy_payroll_retirement',
    rr.update_time = NOW()
WHERE rr.deleted = 0
  AND r.code IN ('view.payroll.batches', 'menu.payroll.import', 'menu.payroll.reports');

UPDATE sys_user_resource ur
JOIN sys_resource r ON r.id = ur.resource_id
SET ur.deleted = 1,
    ur.update_by = 'legacy_payroll_retirement',
    ur.update_time = NOW()
WHERE ur.deleted = 0
  AND r.code IN ('view.payroll.batches', 'menu.payroll.import', 'menu.payroll.reports');

COMMIT;
