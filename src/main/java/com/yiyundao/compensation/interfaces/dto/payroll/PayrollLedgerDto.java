package com.yiyundao.compensation.interfaces.dto.payroll;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PayrollLedgerDto {
    private Long batchId;
    private Integer batchRevision;
    private String inputSnapshotHash;
    private String ruleSnapshotHash;
    private String calculationEngineVersion;
    private String status;
    private String periodLabel;
    private String currency;
    private Integer totalEmployees;
    private BigDecimal earningsTotal;
    private BigDecimal deductionsTotal;
    private BigDecimal grossTotal;
    private BigDecimal taxTotal;
    private BigDecimal socialTotal;
    private BigDecimal netTotal;
    private Integer linesWithWarnings;
    private Integer linesWithBlockingIssues;
    private Integer totalWarnings;
    private Integer blockingIssueCount;
    private Integer reviewIssueCount;
    private Boolean hasBlockingIssues;
    private List<String> warnings;
    private List<PayrollValidationIssueDto> issues;
    private List<PayrollPreviewDto.PayrollPreviewLineDto> lines;
}
