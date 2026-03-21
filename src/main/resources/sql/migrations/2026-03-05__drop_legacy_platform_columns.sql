-- =====================================================
-- Legacy platform columns cleanup (Phase-B)
-- Preconditions:
-- 1) All runtime read/write paths switched to external_identity
-- 2) Data checks passed:
--    - old non-null rows all have active identity mapping
--    - no identity key conflicts / no orphan references
-- 3) Gray release observation completed and rollback prepared
-- =====================================================

-- Optional pre-checks (run manually before executing DDL):
-- SELECT COUNT(*) FROM sys_user
--  WHERE deleted=0 AND platform_type IS NOT NULL AND platform_type<>'' AND platform_user_id IS NOT NULL AND platform_user_id<>''
--    AND NOT EXISTS (
--      SELECT 1 FROM external_identity ei
--       WHERE ei.deleted=0 AND ei.status='active'
--         AND ei.user_id=sys_user.id
--         AND ei.provider=LOWER(sys_user.platform_type)
--         AND ei.subject_id=sys_user.platform_user_id
--    );
-- SELECT COUNT(*) FROM employee
--  WHERE deleted=0 AND platform_type IS NOT NULL AND platform_type<>'' AND platform_user_id IS NOT NULL AND platform_user_id<>''
--    AND NOT EXISTS (
--      SELECT 1 FROM external_identity ei
--       WHERE ei.deleted=0 AND ei.status='active'
--         AND ei.employee_id=employee.id
--         AND ei.provider=LOWER(employee.platform_type)
--         AND ei.subject_id=employee.platform_user_id
--    );

-- Drop indexes on legacy columns first
ALTER TABLE sys_user DROP INDEX idx_platform_user;
ALTER TABLE employee DROP INDEX idx_platform_user;

-- MySQL 8.0.29+ supports INSTANT drop column (server 8.0.41 verified)
ALTER TABLE sys_user
    DROP COLUMN platform_type,
    DROP COLUMN platform_user_id,
    ALGORITHM=INSTANT;

ALTER TABLE employee
    DROP COLUMN platform_type,
    DROP COLUMN platform_user_id,
    ALGORITHM=INSTANT;
