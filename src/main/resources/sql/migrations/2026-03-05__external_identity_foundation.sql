-- external_identity 基础表与历史数据回填（幂等）
-- 目标：支持员工/用户与多平台账号的一对多绑定
-- 说明：
-- 1) 该脚本为增量脚本，不会删除历史数据
-- 2) tenant_key 当前统一回填为 default，后续可按企业维度扩展

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `external_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `provider` varchar(20) NOT NULL COMMENT '平台标识(wechat/dingtalk/feishu)',
  `tenant_key` varchar(100) NOT NULL DEFAULT 'default' COMMENT '租户标识(如corpId/appId/appKey)',
  `subject_type` varchar(30) NOT NULL DEFAULT 'platform_user_id' COMMENT '主体类型(user_id/open_id/union_id/platform_user_id)',
  `subject_id` varchar(191) NOT NULL COMMENT '平台主体ID',
  `employee_id` bigint DEFAULT NULL COMMENT '关联员工ID',
  `user_id` bigint DEFAULT NULL COMMENT '关联用户ID',
  `is_primary` tinyint(1) NOT NULL DEFAULT '1' COMMENT '同平台主账号标记',
  `status` varchar(20) NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
  `source` varchar(20) DEFAULT NULL COMMENT '来源(sync/manual/oauth/migration/approval)',
  `bound_at` datetime DEFAULT NULL COMMENT '绑定时间',
  `unbound_at` datetime DEFAULT NULL COMMENT '解绑时间',
  `last_seen_at` datetime DEFAULT NULL COMMENT '最近使用时间',
  `ext_json` json DEFAULT NULL COMMENT '扩展字段',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_subject` (`provider`, `tenant_key`, `subject_type`, `subject_id`, `deleted`),
  KEY `idx_employee_provider_status` (`employee_id`, `provider`, `status`),
  KEY `idx_user_provider_status` (`user_id`, `provider`, `status`),
  KEY `idx_subject_lookup` (`provider`, `tenant_key`, `subject_type`, `status`),
  CONSTRAINT `fk_external_identity_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`),
  CONSTRAINT `fk_external_identity_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部平台身份绑定表';

-- 历史回填：sys_user 单平台字段 -> external_identity（按平台+账号去重）
INSERT INTO `external_identity`
(`provider`, `tenant_key`, `subject_type`, `subject_id`, `employee_id`, `user_id`,
 `is_primary`, `status`, `source`, `bound_at`, `last_seen_at`, `create_by`, `update_by`)
SELECT
  t.provider,
  'default' AS tenant_key,
  'platform_user_id' AS subject_type,
  t.subject_id,
  t.employee_id,
  t.user_id,
  1 AS is_primary,
  'active' AS status,
  'migration' AS source,
  NOW() AS bound_at,
  NOW() AS last_seen_at,
  'migration' AS create_by,
  'migration' AS update_by
FROM (
  SELECT
    su.platform_type AS provider,
    su.platform_user_id AS subject_id,
    MIN(su.employee_id) AS employee_id,
    MIN(su.id) AS user_id
  FROM `sys_user` su
  WHERE su.deleted = 0
    AND su.platform_type IS NOT NULL AND su.platform_type <> ''
    AND su.platform_user_id IS NOT NULL AND su.platform_user_id <> ''
  GROUP BY su.platform_type, su.platform_user_id
) t
LEFT JOIN `external_identity` ei
  ON ei.deleted = 0
 AND ei.provider = t.provider
 AND ei.tenant_key = 'default'
 AND ei.subject_type = 'platform_user_id'
 AND ei.subject_id = t.subject_id
WHERE ei.id IS NULL;

-- 历史回填：employee 单平台字段补齐（仅在 external_identity 中不存在时插入）
INSERT INTO `external_identity`
(`provider`, `tenant_key`, `subject_type`, `subject_id`, `employee_id`, `user_id`,
 `is_primary`, `status`, `source`, `bound_at`, `last_seen_at`, `create_by`, `update_by`)
SELECT
  t.provider,
  'default' AS tenant_key,
  'platform_user_id' AS subject_type,
  t.subject_id,
  t.employee_id,
  NULL AS user_id,
  1 AS is_primary,
  'active' AS status,
  'migration' AS source,
  NOW() AS bound_at,
  NOW() AS last_seen_at,
  'migration' AS create_by,
  'migration' AS update_by
FROM (
  SELECT
    e.platform_type AS provider,
    e.platform_user_id AS subject_id,
    MIN(e.id) AS employee_id
  FROM `employee` e
  WHERE e.deleted = 0
    AND e.platform_type IS NOT NULL AND e.platform_type <> ''
    AND e.platform_user_id IS NOT NULL AND e.platform_user_id <> ''
  GROUP BY e.platform_type, e.platform_user_id
) t
LEFT JOIN `external_identity` ei
  ON ei.deleted = 0
 AND ei.provider = t.provider
 AND ei.tenant_key = 'default'
 AND ei.subject_type = 'platform_user_id'
 AND ei.subject_id = t.subject_id
WHERE ei.id IS NULL;

SET FOREIGN_KEY_CHECKS = 1;
