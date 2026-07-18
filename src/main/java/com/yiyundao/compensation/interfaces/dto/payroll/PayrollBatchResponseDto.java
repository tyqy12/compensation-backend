package com.yiyundao.compensation.interfaces.dto.payroll;

import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollCalculationStatus;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
@Builder
public class PayrollBatchResponseDto {

    private Long id;
    private Long payCycleId;
    private Long ruleTemplateId;
    private Long ruleTemplateVersion;
    private PayCycleContextDto payCycle;
    private String periodLabel;
    private String type;
    private String scopeJson;
    private String currency;
    private LocalDate payDate;
    private Integer taxYear;
    private Integer taxMonth;
    private Long taxWithholdingEntityId;
    private Integer taxBasicDeductionMonths;
    private Long policyPackageId;
    private String calculationStatus;
    private Integer batchRevision;
    private String inputSnapshotHash;
    private String ruleSnapshotHash;
    private String calculationEngineVersion;
    private String status;
    private Long approvalWorkflowId;
    private String paymentBatchNo;
    private String paymentStatus;
    private String settlementProviderCode;
    private Boolean confirmationRequired;
    private String confirmationMode;
    private LocalDateTime confirmationCompletedTime;
    private String remark;
    private String resultHash;
    private Boolean immutableFlag;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static PayrollBatchResponseDto from(PayrollBatch batch) {
        return from(batch, null);
    }

    public static PayrollBatchResponseDto from(PayrollBatch batch, PayCycleContextDto payCycle) {
        if (batch == null) {
            return null;
        }
        return PayrollBatchResponseDto.builder()
                .id(batch.getId())
                .payCycleId(batch.getPayCycleId())
                .ruleTemplateId(batch.getRuleTemplateId())
                .ruleTemplateVersion(batch.getRuleTemplateVersion())
                .payCycle(payCycle)
                .periodLabel(batch.getPeriodLabel())
                .type(batch.getType())
                .scopeJson(batch.getScopeJson())
                .currency(batch.getCurrency())
                .payDate(batch.getPayDate())
                .taxYear(batch.getTaxYear())
                .taxMonth(batch.getTaxMonth())
                .taxWithholdingEntityId(batch.getTaxWithholdingEntityId())
                .taxBasicDeductionMonths(batch.getTaxBasicDeductionMonths())
                .policyPackageId(batch.getPolicyPackageId())
                .calculationStatus(code(batch.getCalculationStatus()))
                .batchRevision(batch.getBatchRevision())
                .inputSnapshotHash(batch.getInputSnapshotHash())
                .ruleSnapshotHash(batch.getRuleSnapshotHash())
                .calculationEngineVersion(batch.getCalculationEngineVersion())
                .status(code(batch.getStatus()))
                .approvalWorkflowId(batch.getApprovalWorkflowId())
                .paymentBatchNo(batch.getPaymentBatchNo())
                .paymentStatus(code(batch.getPaymentStatus()))
                .settlementProviderCode(batch.getSettlementProviderCode())
                .confirmationRequired(batch.getConfirmationRequired())
                .confirmationMode(batch.getConfirmationMode())
                .confirmationCompletedTime(batch.getConfirmationCompletedTime())
                .remark(batch.getRemark())
                .resultHash(batch.getResultHash())
                .immutableFlag(batch.getImmutableFlag())
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

    private static String code(com.yiyundao.compensation.enums.PaymentBatchProcessStatus status) {
        return status == null ? null : status.getCode();
    }
}
