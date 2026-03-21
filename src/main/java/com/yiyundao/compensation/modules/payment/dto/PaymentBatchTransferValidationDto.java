package com.yiyundao.compensation.modules.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentBatchTransferValidationDto {

    private String batchNo;
    private Integer pendingCount;
    private Integer passCount;
    private Integer blockedCount;
    private Boolean pass;
    private List<String> warnings;
    private List<TransferValidationIssueDto> blockedRecords;
}

