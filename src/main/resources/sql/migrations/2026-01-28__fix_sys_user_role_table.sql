-- ============================================================================
-- 修复 sys_user_role 表结构
-- 问题：表缺少 id 主键列和 BaseEntity 必要的审计字段
-- 执行时间：2026-01-28
-- ============================================================================

-- 1. 添加缺失的列（如果不存在）
ALTER TABLE sys_user_role
ADD COLUMN IF NOT EXISTS `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID' FIRST,
ADD COLUMN IF NOT EXISTS `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
ADD COLUMN IF NOT EXISTS `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
ADD COLUMN IF NOT EXISTS `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
ADD COLUMN IF NOT EXISTS `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
ADD COLUMN IF NOT EXISTS `version` int DEFAULT '0' COMMENT '乐观锁版本号';

-- 2. 将 id 列设为主键（如果还不是主键）
ALTER TABLE sys_user_role
DROP PRIMARY KEY,
ADD PRIMARY KEY (`id`);

-- 3. 添加备注列（如果不存在）
ALTER TABLE sys_user_role
ADD COLUMN IF NOT EXISTS `granted_by` bigint DEFAULT NULL COMMENT '授权人ID',
ADD COLUMN IF NOT EXISTS `granted_at` datetime DEFAULT NULL COMMENT '授权时间',
ADD COLUMN IF NOT EXISTS `expires_at` datetime DEFAULT NULL COMMENT '过期时间',
ADD COLUMN IF NOT EXISTS `remarks` varchar(500) DEFAULT NULL COMMENT '备注',
ADD COLUMN IF NOT EXISTS `delete_by` varchar(50) DEFAULT NULL COMMENT '删除人',
ADD COLUMN IF NOT EXISTS `delete_time` datetime DEFAULT NULL COMMENT '删除时间';

-- 4. 验证表结构
SELECT
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_KEY,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sys_user_role'
ORDER BY ORDINAL_POSITION;

-- 5. 清理测试数据（如果有旧数据）
-- 注意：如果之前插入过数据，现在需要重新插入以确保 deleted=0
-- INSERT IGNORE 会自动跳过已存在的记录
