-- 添加 payroll_batch.payment_batch_no 唯一约束
-- 用于防止并发场景下的重复支付批次创建
--
-- 作者: 芙宁娜
-- 日期: 2026-02-01
-- 相关问题: 并发场景下的重复支付风险

-- 检查是否已存在唯一约束
SET @constraint_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'payroll_batch'
      AND CONSTRAINT_NAME = 'uk_payment_batch_no'
      AND CONSTRAINT_TYPE = 'UNIQUE'
);

-- 如果不存在，则添加唯一约束
SET @sql = IF(
    @constraint_exists = 0,
    'ALTER TABLE payroll_batch ADD UNIQUE KEY uk_payment_batch_no (payment_batch_no)',
    'SELECT "唯一约束 uk_payment_batch_no 已存在，跳过创建" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 验证约束是否创建成功
SELECT
    CONSTRAINT_NAME,
    CONSTRAINT_TYPE,
    TABLE_NAME
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'payroll_batch'
  AND CONSTRAINT_NAME = 'uk_payment_batch_no';
