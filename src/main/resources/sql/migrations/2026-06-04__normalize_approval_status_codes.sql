-- Normalize approval status values to the lowercase codes persisted by ApprovalStatus.code.
-- This repairs rows that may have been written by the default enum-name handler before @EnumValue was added.

SET NAMES utf8mb4;
START TRANSACTION;

UPDATE approval_workflow
SET status = LOWER(status),
    update_time = NOW()
WHERE BINARY status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'SKIPPED');

UPDATE approval_step
SET status = LOWER(status),
    update_time = NOW()
WHERE BINARY status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'SKIPPED');

COMMIT;
