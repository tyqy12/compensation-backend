-- 薪资规则包版本与发薪日历绑定。
-- 规则版本快照不可变；历史周期/批次无法可靠推断规则归属时保持未绑定，待业务确认后补录。

SET NAMES utf8mb4;
SET @db := DATABASE();

CREATE TABLE IF NOT EXISTS `salary_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_id` bigint NOT NULL COMMENT '薪资规则包ID',
  `version_no` bigint NOT NULL COMMENT '规则包数据版本号',
  `name` varchar(100) NOT NULL COMMENT '规则包名称快照',
  `type` varchar(20) NOT NULL COMMENT '适用用工类型',
  `items_json` json DEFAULT NULL COMMENT '薪资项规则快照',
  `tax_rule_json` json DEFAULT NULL COMMENT '税社保规则快照',
  `status` varchar(20) DEFAULT 'disabled' COMMENT '生成快照时的规则包状态',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(50) DEFAULT NULL,
  `update_by` varchar(50) DEFAULT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `version` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_version` (`template_id`,`version_no`),
  KEY `idx_template_type_version` (`type`,`version_no`),
  KEY `idx_template_version_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薪资规则包不可变版本快照';

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND COLUMN_NAME='rule_template_id'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `pay_cycle` ADD COLUMN `rule_template_id` bigint DEFAULT NULL COMMENT ''绑定的薪资规则包ID'' AFTER `type`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND COLUMN_NAME='rule_template_version'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `pay_cycle` ADD COLUMN `rule_template_version` bigint DEFAULT NULL COMMENT ''绑定的薪资规则包版本'' AFTER `rule_template_id`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='rule_template_id'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD COLUMN `rule_template_id` bigint DEFAULT NULL COMMENT ''批次锁定的薪资规则包ID'' AFTER `pay_cycle_id`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND COLUMN_NAME='rule_template_version'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD COLUMN `rule_template_version` bigint DEFAULT NULL COMMENT ''批次锁定的薪资规则包版本'' AFTER `rule_template_id`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND COLUMN_NAME='template_version'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD COLUMN `template_version` bigint DEFAULT NULL COMMENT ''工资行使用的规则包版本'' AFTER `template_id`',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='pay_cycle' AND INDEX_NAME='idx_cycle_rule_template'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `pay_cycle` ADD KEY `idx_cycle_rule_template` (`rule_template_id`,`rule_template_version`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_batch' AND INDEX_NAME='idx_batch_rule_template'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_batch` ADD KEY `idx_batch_rule_template` (`rule_template_id`,`rule_template_version`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@db AND TABLE_NAME='payroll_line' AND INDEX_NAME='idx_line_template_version'
);
SET @sql := IF(@exists=0,
  'ALTER TABLE `payroll_line` ADD KEY `idx_line_template_version` (`template_id`,`template_version`)',
  'SELECT 1 AS noop');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `salary_template`
SET `data_version` = 1
WHERE `data_version` IS NULL;

INSERT INTO `salary_template_version`
    (`template_id`, `version_no`, `name`, `type`, `items_json`, `tax_rule_json`, `status`,
     `create_time`, `update_time`, `create_by`, `update_by`, `deleted`, `version`)
SELECT st.`id`, COALESCE(st.`data_version`, 1), st.`name`, st.`type`, st.`items_json`, st.`tax_rule_json`,
       COALESCE(st.`status`, 'disabled'), st.`create_time`, st.`update_time`, st.`create_by`, st.`update_by`,
       COALESCE(st.`deleted`, 0), COALESCE(st.`version`, 0)
FROM `salary_template` st
WHERE NOT EXISTS (
    SELECT 1
    FROM `salary_template_version` stv
    WHERE stv.`template_id` = st.`id`
      AND stv.`version_no` = COALESCE(st.`data_version`, 1)
);

-- 兼容旧模型：历史 pay_cycle.type 曾被当作月度频率使用。
-- 只有所有关联批次的用工类型唯一时才转换，原频率保留在 cycle_type。
UPDATE `pay_cycle` pc
JOIN (
    SELECT `pay_cycle_id`, MIN(`type`) AS `payroll_type`
    FROM `payroll_batch`
    WHERE `deleted` = 0 AND `pay_cycle_id` IS NOT NULL
    GROUP BY `pay_cycle_id`
    HAVING COUNT(DISTINCT `type`) = 1
) legacy ON legacy.`pay_cycle_id` = pc.`id`
SET pc.`type` = legacy.`payroll_type`
WHERE pc.`deleted` = 0
  AND pc.`type` IN ('monthly', 'custom')
  AND legacy.`payroll_type` IN ('full_time', 'part_time', 'contractor');

-- 只有周期 type 与规则包 type 完全一致，且该类型恰好一个启用规则包时才自动回填。
-- monthly/custom 等历史周期没有足够信息推断 full_time/part_time，必须人工绑定。
UPDATE `pay_cycle` pc
JOIN (
    SELECT `type`, MAX(`id`) AS `template_id`, MAX(COALESCE(`data_version`, 1)) AS `version_no`
    FROM `salary_template`
    WHERE `deleted` = 0 AND `status` = 'enabled'
    GROUP BY `type`
    HAVING COUNT(*) = 1
) st ON st.`type` = pc.`type`
SET pc.`rule_template_id` = st.`template_id`,
    pc.`rule_template_version` = st.`version_no`
WHERE pc.`rule_template_id` IS NULL
  AND pc.`deleted` = 0;

UPDATE `payroll_batch` pb
JOIN `pay_cycle` pc ON pc.`id` = pb.`pay_cycle_id`
SET pb.`rule_template_id` = pc.`rule_template_id`,
    pb.`rule_template_version` = pc.`rule_template_version`
WHERE pb.`rule_template_id` IS NULL
  AND pc.`rule_template_id` IS NOT NULL;

UPDATE `payroll_line` pl
JOIN `salary_template` st ON st.`id` = pl.`template_id`
SET pl.`template_version` = COALESCE(st.`data_version`, 1)
WHERE pl.`template_version` IS NULL;
