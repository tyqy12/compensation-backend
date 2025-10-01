package com.yiyundao.compensation.interfaces.dto.payroll;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PayrollLedgerDto {
    private Long batchId;
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
    private Integer totalWarnings;
    private List<String> warnings;
    private List<PayrollPreviewDto.PayrollPreviewLineDto> lines;
}

