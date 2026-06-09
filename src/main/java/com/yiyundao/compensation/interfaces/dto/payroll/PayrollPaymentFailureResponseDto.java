package com.yiyundao.compensation.interfaces.dto.payroll;

import com.yiyundao.compensation.modules.payroll.entity.PayrollPaymentFailure;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PayrollPaymentFailureResponseDto {

    private Long id;
    private Long workflowId;
    private Long payrollBatchId;
    private String businessKey;
    private String errorMessage;
    private String status;
    private Integer retryCount;
    private LocalDateTime lastFailedTime;
    private LocalDateTime lastRetryTime;
    private LocalDateTime resolvedTime;
    private String paymentBatchNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static PayrollPaymentFailureResponseDto from(PayrollPaymentFailure failure) {
        if (failure == null) {
            return null;
        }
        return PayrollPaymentFailureResponseDto.builder()
                .id(failure.getId())
                .workflowId(failure.getWorkflowId())
                .payrollBatchId(failure.getPayrollBatchId())
                .businessKey(failure.getBusinessKey())
                .errorMessage(failure.getErrorMessage())
                .status(failure.getStatus())
                .retryCount(failure.getRetryCount())
                .lastFailedTime(failure.getLastFailedTime())
                .lastRetryTime(failure.getLastRetryTime())
                .resolvedTime(failure.getResolvedTime())
                .paymentBatchNo(failure.getPaymentBatchNo())
                .createTime(failure.getCreateTime())
                .updateTime(failure.getUpdateTime())
                .build();
    }
}
