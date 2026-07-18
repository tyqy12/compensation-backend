package com.yiyundao.compensation.interfaces.dto.payroll;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PayrollPreviewDto {
    private Long batchId;
    private String currency;
    private Integer totalEmployees;
    private Integer linesWithWarnings;
    private Integer linesWithBlockingIssues;
    private Integer totalWarnings;
    private Integer blockingIssueCount;
    private Integer reviewIssueCount;
    private Boolean hasBlockingIssues;
    private List<String> warnings;
    private List<PayrollValidationIssueDto> issues;
    private BigDecimal earningsTotal;
    private BigDecimal deductionsTotal;
    private BigDecimal grossTotal;
    private BigDecimal taxTotal;
    private BigDecimal socialTotal;
    private BigDecimal netTotal;
    private List<PayrollPreviewLineDto> lines;

    @Data
    public static class PayrollPreviewLineDto {
        private Long employeeId;
        private String employeeNo;
        private String employeeName;
        private List<PayrollPreviewItemDto> items;
        private BigDecimal earningsTotal;
        private BigDecimal deductionsTotal;
        private BigDecimal grossAmount;
        private BigDecimal taxAmount;
        private BigDecimal socialAmount;
        private BigDecimal netAmount;
        private TaxBreakdownDto taxBreakdown;
        // warnings and diffs
        private List<String> warnings; // 缺失项/阈值/异常提示
        private List<PayrollValidationIssueDto> issues;
        private Integer blockingIssueCount;
        private Integer reviewIssueCount;
        private Boolean hasBlockingIssues;
        private List<String> missingItems; // 模板要求但缺失的项编码
        private DiffSummaryDto diff; // 与上期对比
        private String department;
        private Long managerId;
        private String managerName;
        private String employmentType;
    }

    @Data
    public static class PayrollPreviewItemDto {
        private String code;
        private String name;
        private String type; // earning/deduction
        private Boolean taxable;
        private BigDecimal amount;
    }

    @Data
    public static class DiffSummaryDto {
        private BigDecimal lastGrossAmount;
        private BigDecimal lastNetAmount;
        private BigDecimal netDeltaAmount;
        private BigDecimal netDeltaPercent; // -1.00 ~ +1.00
    }

    @Data
    public static class TaxBreakdownDto {
        private String mode;
        private Integer taxYear;
        private Integer taxMonth;
        private BigDecimal cumulativeTaxableIncome;
        private BigDecimal rate;
        private BigDecimal quickDeduction;
        private BigDecimal cumulativeTax;
        private BigDecimal currentWithholdingTax;
        private Integer bracketLevel;
        private String formula;
        private String policyCode;
    }
}
