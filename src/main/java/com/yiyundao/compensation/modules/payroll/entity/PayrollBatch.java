package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PayrollCalculationStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_batch")
public class PayrollBatch extends BaseEntity {
    @TableField("pay_cycle_id")
    private Long payCycleId;
    @TableField("rule_template_id")
    private Long ruleTemplateId;
    @TableField("rule_template_version")
    private Long ruleTemplateVersion;
    private String periodLabel;
    private String type; // full_time/part_time
    @TableField("scope_json")
    private String scopeJson;
    private String currency;

    @TableField("calculation_status")
    private PayrollCalculationStatus calculationStatus;

    @TableField("batch_revision")
    private Integer batchRevision;

    /** 输入事实快照的 SHA-256，锁定后用于检测输入被意外修改。 */
    @TableField("input_snapshot_hash")
    private String inputSnapshotHash;

    /** 计算时使用的输入事实完整快照，作为审计证据保留。 */
    @TableField("input_snapshot_json")
    private String inputSnapshotJson;

    /** 薪资模板与税社保基础规则快照的 SHA-256。 */
    @TableField("rule_snapshot_hash")
    private String ruleSnapshotHash;

    /** 计算时使用的薪资规则完整快照，作为审计证据保留。 */
    @TableField("rule_snapshot_json")
    private String ruleSnapshotJson;

    /** 计算实现版本，不等同于数据库乐观锁 version。 */
    @TableField("calculation_engine_version")
    private String calculationEngineVersion;

    /** 支付子域状态投影，不参与薪资规则计算。 */
    @TableField("payment_status")
    private PaymentBatchProcessStatus paymentStatus;

    private PayrollBatchStatus status; // 使用枚举类型，MyBatis-Plus会自动处理与数据库的转换

    @TableField("approval_workflow_id")
    private Long approvalWorkflowId;
    @TableField("payment_batch_no")
    private String paymentBatchNo;
    @TableField("settlement_provider_code")
    private String settlementProviderCode;
    @TableField("confirmation_required")
    private Boolean confirmationRequired;
    @TableField("confirmation_mode")
    private String confirmationMode;
    @TableField("confirmation_completed_time")
    private LocalDateTime confirmationCompletedTime;
    private String remark;
}
