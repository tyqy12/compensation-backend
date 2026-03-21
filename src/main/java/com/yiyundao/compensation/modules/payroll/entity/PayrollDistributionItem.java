package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import com.yiyundao.compensation.enums.PayrollDistributionItemStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_distribution_item")
public class PayrollDistributionItem extends BaseEntity {

    @TableField("distribution_id")
    private Long distributionId;

    @TableField("employee_id")
    private Long employeeId;

    @TableField("line_id")
    private Long lineId;

    @TableField("employee_name")
    private String employeeName;

    @TableField("recipient_name")
    private String recipientName;

    @TableField("account_no_encrypted")
    private String accountNoEncrypted;

    @TableField("account_no_masked")
    private String accountNoMasked;

    @TableField("account_type")
    private String accountType;

    @TableField("payment_method")
    private String paymentMethod;

    @TableField("provider_code")
    private String providerCode;

    private BigDecimal amount;

    @TableField("item_status")
    private PayrollDistributionItemStatus itemStatus;

    @TableField("payment_record_id")
    private Long paymentRecordId;

    @TableField("failure_reason")
    private String failureReason;

    @TableField("retry_count")
    private Integer retryCount;
}
