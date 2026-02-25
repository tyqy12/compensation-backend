-- 密码重置脚本
-- 执行方式: mysql -u username -p database_name < reset_password.sql
-- 或者在 MySQL 客户端中执行

UPDATE s06.sys_user
SET password = '$2b$12$RWa0sb4DssDIBXEEJqpuSe5ky/tg46/ZTgnwTQfDJS2Gkmx5PNOaK'
WHERE username = 'admin';

-- 验证更新结果
SELECT id, username, password, status FROM s06.sys_user WHERE username = 'admin';
