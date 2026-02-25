-- RBAC 资源模型与授权相关表
-- 目标：支持菜单/页面/按钮/接口的资源化管理、角色授权、用户特定授权、授权审批快照
-- 兼容：仅创建不存在的表；对已有表的列新增采用 information_schema 判断
-- 运行：mysql -h<host> -u<user> -p<pass> <db> < src/main/resources/sql/migrations/2025-09-29__rbac_resources.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1) 资源表：菜单/页面/按钮/接口统一抽象
CREATE TABLE IF NOT EXISTS `sys_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `type` varchar(20) NOT NULL COMMENT '资源类型: MENU/VIEW/ACTION/API',
  `code` varchar(100) NOT NULL COMMENT '全局唯一编码',
  `name` varchar(100) NOT NULL COMMENT '资源名称',
  `path` varchar(255) DEFAULT NULL COMMENT '前端路由或后端接口路径',
  `component` varchar(255) DEFAULT NULL COMMENT '前端组件',
  `icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `parent_id` bigint DEFAULT NULL COMMENT '父资源ID',
  `order_num` int DEFAULT 0 COMMENT '排序号',
  `props_json` json DEFAULT NULL COMMENT '扩展元信息(JSON)',
  `status` varchar(20) DEFAULT 'enabled' COMMENT '状态: enabled/disabled',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_code` (`code`),
  KEY `idx_type` (`type`),
  KEY `idx_parent_order` (`parent_id`, `order_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统资源表';

-- 2) 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) NOT NULL COMMENT '角色编码',
  `name` varchar(100) NOT NULL COMMENT '角色名称',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `status` varchar(20) DEFAULT 'enabled' COMMENT '状态',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 3) 角色-资源授权（可含按钮 actions）
CREATE TABLE IF NOT EXISTS `sys_role_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint NOT NULL,
  `resource_id` bigint NOT NULL,
  `actions_json` json DEFAULT NULL COMMENT '按钮/动作集合(JSON 数组)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_resource` (`role_id`, `resource_id`),
  KEY `idx_role` (`role_id`),
  KEY `idx_resource` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-资源授权关系';

-- 4) 用户-角色
CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关系';

-- 5) 用户-资源（个性授权/禁用）
CREATE TABLE IF NOT EXISTS `sys_user_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL,
  `resource_id` bigint NOT NULL,
  `actions_json` json DEFAULT NULL COMMENT '按钮/动作集合(JSON 数组)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_resource` (`user_id`, `resource_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_resource` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-资源个性授权';

-- 6) 授权变更快照（审批配套）
CREATE TABLE IF NOT EXISTS `resource_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `workflow_id` bigint NOT NULL COMMENT '审批流程ID',
  `before_json` json DEFAULT NULL COMMENT '变更前快照',
  `after_json` json DEFAULT NULL COMMENT '变更后快照(拟变更)',
  `actor_id` bigint DEFAULT NULL COMMENT '发起人',
  `reason` varchar(255) DEFAULT NULL COMMENT '原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_workflow` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源授权变更快照';

-- 7) sys_user 增加 permission_version（授权版本，变更+1 用于缓存）
SET @db := DATABASE();
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='sys_user' AND COLUMN_NAME='permission_version'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `sys_user` ADD COLUMN `permission_version` INT DEFAULT 0 COMMENT ''权限版本'' AFTER `platform_type`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;

