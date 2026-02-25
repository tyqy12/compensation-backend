-- 添加 payroll_line 表的 warning 字段
-- 用于存储薪资计算过程中的预警信息
-- 预警可能包括：税率异常、金额异常、社保基数异常等

ALTER TABLE payroll_line
ADD COLUMN warning VARCHAR(500) NULL DEFAULT NULL COMMENT '预警信息' AFTER note;

-- 创建索引方便查询有预警的记录
CREATE INDEX idx_payroll_line_warning ON payroll_line(warning) WHERE warning IS NOT NULL;
