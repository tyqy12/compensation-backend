-- 失败支付记录允许暂时没有收款账号。
-- 这类记录不会进入渠道提交，补充员工收款信息后由重试流程重新校验路由。

SET NAMES utf8mb4;
SET @db := DATABASE();

SET @sql := (
  SELECT IF(COUNT(*) = 1,
    'ALTER TABLE `payment_record` MODIFY COLUMN `recipient_account` varchar(100) DEFAULT NULL COMMENT ''收款账户；校验失败记录允许为空，补充收款信息后可重试''',
    'SELECT 1 AS noop')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'payment_record'
    AND COLUMN_NAME = 'recipient_account'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
