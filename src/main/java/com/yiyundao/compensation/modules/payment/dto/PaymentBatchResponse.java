package com.yiyundao.compensation.modules.payment.dto;

import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PaymentType;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentBatchResponse {

    private Long id;
    private String batchNo;
    private String batchName;
    private String paymentType;
    private BigDecimal totalAmount;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private String status;
    private Long distributionId;
    private String paymentStatus;
    private LocalDateTime submitTime;
    private LocalDateTime approveTime;
    private LocalDateTime processStartTime;
    private LocalDateTime processEndTime;
    private Long approverId;
    private Long processorId;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static PaymentBatchResponse from(PaymentBatch batch) {
        if (batch == null) {
            return null;
        }
        return PaymentBatchResponse.builder()
                .id(batch.getId())
                .batchNo(batch.getBatchNo())
                .batchName(batch.getBatchName())
                .paymentType(code(batch.getPaymentType()))
                .totalAmount(batch.getTotalAmount())
                .totalCount(batch.getTotalCount())
                .successCount(batch.getSuccessCount())
                .failedCount(batch.getFailedCount())
                .status(code(batch.getStatus()))
                .distributionId(batch.getDistributionId())
                .paymentStatus(code(batch.getPaymentStatus()))
                .submitTime(batch.getSubmitTime())
                .approveTime(batch.getApproveTime())
                .processStartTime(batch.getProcessStartTime())
                .processEndTime(batch.getProcessEndTime())
                .approverId(batch.getApproverId())
                .processorId(batch.getProcessorId())
                .remark(batch.getRemark())
                .createTime(batch.getCreateTime())
                .updateTime(batch.getUpdateTime())
                .build();
    }

    private static String code(PaymentType type) {
        return type == null ? null : type.getCode();
    }

    private static String code(BatchStatus status) {
        return status == null ? null : status.getCode();
    }

    private static String code(PaymentBatchProcessStatus status) {
        return status == null ? null : status.getCode();
    }
}
