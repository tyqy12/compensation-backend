package com.yiyundao.compensation.modules.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferValidationIssueDto {

    private Long recordId;
    private Long employeeId;
    private String employeeName;
    private String providerCode;
    private String paymentMethod;
    private String recipientAccountMasked;
    private String errorCode;
    private String errorMsg;
}

