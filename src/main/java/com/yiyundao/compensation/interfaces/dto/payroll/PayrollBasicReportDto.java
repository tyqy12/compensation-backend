package com.yiyundao.compensation.interfaces.dto.payroll;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PayrollBasicReportDto {
    private Long batchId;
    private String periodLabel;
    private String currency;
    private BigDecimal earningsTotal;
    private BigDecimal deductionsTotal;
    private BigDecimal grossTotal;
    private BigDecimal taxTotal;
    private BigDecimal socialTotal;
    private BigDecimal netTotal;
    private Integer employeeCount;
    private List<DepartmentSummary> departments;

    @Data
    public static class DepartmentSummary {
        private String department;
        private Integer employeeCount;
        private BigDecimal earningsTotal;
        private BigDecimal deductionsTotal;
        private BigDecimal grossTotal;
        private BigDecimal taxTotal;
        private BigDecimal socialTotal;
        private BigDecimal netTotal;
    }
}

