package com.yiyundao.compensation.modules.payroll.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yiyundao.compensation.entity.BaseEntity;
import com.yiyundao.compensation.enums.PayrollConfirmationRecordStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payroll_confirmation_record")
public class PayrollConfirmationRecord extends BaseEntity {

    @TableField("confirmation_id")
    private Long confirmationId;

    @TableField("employee_id")
    private Long employeeId;

    @TableField("line_id")
    private Long lineId;

    @TableField("record_status")
    private PayrollConfirmationRecordStatus recordStatus;

    @TableField("reject_reason")
    private String rejectReason;

    private String comment;

    @TableField("confirmed_at")
    private LocalDateTime confirmedAt;
}
