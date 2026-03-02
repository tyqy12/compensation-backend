package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_line")
public class PayrollLine extends BaseEntity {
    @TableField("batch_id")
    private Long batchId;
    @TableField("employee_id")
    private Long employeeId;
    @TableField("employment_type")
    private String employmentType;
    @TableField("template_id")
    private Long templateId;
    @TableField("items_snapshot_json")
    private String itemsSnapshotJson;
    @TableField("gross_amount")
    private java.math.BigDecimal grossAmount;
    @TableField("tax_amount")
    private java.math.BigDecimal taxAmount;
    @TableField("social_amount")
    private java.math.BigDecimal socialAmount;
    @TableField("net_amount")
    private java.math.BigDecimal netAmount;
    private String currency;
    private String status;
    private String note;
    @TableField("warning")
    private String warning;
    @TableField("confirmation_assignee_employee_id")
    private Long confirmationAssigneeEmployeeId;
    @TableField("confirmation_status")
    private String confirmationStatus;
    @TableField("confirmed_by_user_id")
    private Long confirmedByUserId;
    @TableField("confirmed_by_employee_id")
    private Long confirmedByEmployeeId;
    @TableField("confirmed_at")
    private LocalDateTime confirmedAt;
    @TableField("confirmation_comment")
    private String confirmationComment;
    @TableField("objection_reason")
    private String objectionReason;
    @TableField("objection_at")
    private LocalDateTime objectionAt;
    @TableField("dispute_workflow_id")
    private Long disputeWorkflowId;
}
