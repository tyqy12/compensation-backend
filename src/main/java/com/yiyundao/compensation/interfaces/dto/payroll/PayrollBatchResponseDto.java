package com.yiyundao.compensation.interfaces.dto.payroll;

import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollCalculationStatus;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PayrollBatchResponseDto {

    private Long id;
    private Long payCycleId;
    private String periodLabel;
    private String type;
    private String scopeJson;
    private String currency;
    private String calculationStatus;
    private Integer batchRevision;
    private String status;
    private Long approvalWorkflowId;
    private String paymentBatchNo;
    private String settlementProviderCode;
    private Boolean confirmationRequired;
    private String confirmationMode;
    private LocalDateTime confirmationCompletedTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static PayrollBatchResponseDto from(PayrollBatch batch) {
        if (batch == null) {
            return null;
        }
        return PayrollBatchResponseDto.builder()
                .id(batch.getId())
                .payCycleId(batch.getPayCycleId())
                .periodLabel(batch.getPeriodLabel())
                .type(batch.getType())
                .scopeJson(batch.getScopeJson())
                .currency(batch.getCurrency())
                .calculationStatus(code(batch.getCalculationStatus()))
                .batchRevision(batch.getBatchRevision())
                .status(code(batch.getStatus()))
                .approvalWorkflowId(batch.getApprovalWorkflowId())
                .paymentBatchNo(batch.getPaymentBatchNo())
                .settlementProviderCode(batch.getSettlementProviderCode())
                .confirmationRequired(batch.getConfirmationRequired())
                .confirmationMode(batch.getConfirmationMode())
                .confirmationCompletedTime(batch.getConfirmationCompletedTime())
                .remark(batch.getRemark())
                .createTime(batch.getCreateTime())
                .updateTime(batch.getUpdateTime())
                .build();
    }

    private static String code(PayrollCalculationStatus status) {
        return status == null ? null : status.getCode();
    }

    private static String code(PayrollBatchStatus status) {
        return status == null ? null : status.getCode();
    }
}
