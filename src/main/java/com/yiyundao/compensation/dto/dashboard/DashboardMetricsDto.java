package com.yiyundao.compensation.dto.dashboard;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DashboardMetricsDto {
    private Integer employeeTotal;
    private Double employeeGrowthRate;

    private BigDecimal monthlyPaymentAmount;
    private Double monthlyPaymentGrowthRate;

    private Integer pendingBatchCount;
    private Double pendingBatchChangeRate;

    private Double userBindingRate;
    private Double userBindingGrowthRate;
}

