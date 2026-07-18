package com.yiyundao.compensation.modules.payroll.service;

import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public interface PayrollContributionCalculationService {

    Result calculate(Long employeeId,
                     PayrollBatch batch,
                     BigDecimal declaredWage,
                     BigDecimal fallbackEmployeeAmount,
                     RoundingMode roundingMode);

    record Result(
            List<Line> lines,
            BigDecimal employeeAmount,
            BigDecimal employerAmount,
            boolean policyDriven
    ) {
    }

    record Line(
            String contributionType,
            String regionCode,
            Long policyId,
            BigDecimal declaredWage,
            BigDecimal contributionBase,
            BigDecimal employerRate,
            BigDecimal employeeRate,
            BigDecimal employerAmount,
            BigDecimal employeeAmount,
            String formula,
            String calculationHash
    ) {
    }
}
