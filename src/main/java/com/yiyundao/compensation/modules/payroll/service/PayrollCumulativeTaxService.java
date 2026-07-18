package com.yiyundao.compensation.modules.payroll.service;

import com.yiyundao.compensation.modules.payroll.compliance.CumulativeWithholdingTaxCalculator;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;

import java.math.BigDecimal;
import java.math.RoundingMode;

public interface PayrollCumulativeTaxService {
    PayrollCumulativeTaxService.TaxComputation calculate(
            Long employeeId,
            PayrollBatch batch,
            BigDecimal currentIncome,
            BigDecimal currentSpecialDeduction,
            BigDecimal currentSpecialAdditionalDeduction,
            BigDecimal currentOtherDeduction,
            int scale,
            RoundingMode roundingMode
    );

    void recordLedger(Long employeeId,
                      PayrollBatch batch,
                      Long payrollLineId,
                      TaxComputation computation);

    record TaxComputation(
            int taxYear,
            int taxMonth,
            CumulativeWithholdingTaxCalculator.Result result,
            String policyCode,
            Long policyId,
            CumulativeAmounts cumulativeAmounts
    ) {
        public TaxComputation(int taxYear,
                              int taxMonth,
                              CumulativeWithholdingTaxCalculator.Result result,
                              String policyCode) {
            this(taxYear, taxMonth, result, policyCode, null, null);
        }

        public TaxComputation(int taxYear,
                              int taxMonth,
                              CumulativeWithholdingTaxCalculator.Result result,
                              String policyCode,
                              CumulativeAmounts cumulativeAmounts) {
            this(taxYear, taxMonth, result, policyCode, null, cumulativeAmounts);
        }
    }

    record CumulativeAmounts(
            BigDecimal income,
            BigDecimal taxExemptIncome,
            BigDecimal basicDeduction,
            BigDecimal specialDeduction,
            BigDecimal specialAdditionalDeduction,
            BigDecimal otherDeduction,
            BigDecimal taxReduction,
            BigDecimal withheldTax
    ) {
    }
}
