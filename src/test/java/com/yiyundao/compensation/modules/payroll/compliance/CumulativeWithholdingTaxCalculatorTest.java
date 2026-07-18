package com.yiyundao.compensation.modules.payroll.compliance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CumulativeWithholdingTaxCalculatorTest {

    @Test
    void shouldUseTheCorrectBracketAtEveryBoundary() {
        assertThat(calculate("36000").bracketLevel()).isEqualTo(1);
        assertThat(calculate("36000.01").bracketLevel()).isEqualTo(2);
        assertThat(calculate("144000").bracketLevel()).isEqualTo(2);
        assertThat(calculate("144000.01").bracketLevel()).isEqualTo(3);
        assertThat(calculate("300000").bracketLevel()).isEqualTo(3);
        assertThat(calculate("300000.01").bracketLevel()).isEqualTo(4);
    }

    @Test
    void shouldCalculateCurrentTaxAfterPreviousWithheldTax() {
        var result = CumulativeWithholdingTaxCalculator.calculate(
                CumulativeWithholdingTaxCalculator.Input.of(
                        new BigDecimal("120000"),
                        BigDecimal.ZERO,
                        new BigDecimal("60000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("3000")
                )
        );

        assertThat(result.cumulativeTaxableIncome()).isEqualByComparingTo("60000.00");
        assertThat(result.rate()).isEqualByComparingTo("0.10");
        assertThat(result.cumulativeTaxBeforeReduction()).isEqualByComparingTo("3480.00");
        assertThat(result.currentWithholdingTax()).isEqualByComparingTo("480.00");
        assertThat(result.formula()).contains("max(0").contains("0.10");
    }

    @Test
    void shouldNotProduceNegativeTaxWhenDeductionsExceedIncome() {
        var result = CumulativeWithholdingTaxCalculator.calculate(
                CumulativeWithholdingTaxCalculator.Input.of(
                        new BigDecimal("10000"),
                        BigDecimal.ZERO,
                        new BigDecimal("5000"),
                        new BigDecimal("8000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )
        );

        assertThat(result.cumulativeTaxableIncome()).isEqualByComparingTo("0.00");
        assertThat(result.currentWithholdingTax()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldSupportPolicySpecificBracketsWithoutChangingTheCalculator() {
        var brackets = java.util.List.of(
                new CumulativeWithholdingTaxCalculator.TaxBracket(
                        1, new BigDecimal("1000"), new BigDecimal("0.05"), BigDecimal.ZERO),
                new CumulativeWithholdingTaxCalculator.TaxBracket(
                        2, null, new BigDecimal("0.10"), new BigDecimal("50"))
        );
        var result = CumulativeWithholdingTaxCalculator.calculate(
                CumulativeWithholdingTaxCalculator.Input.of(
                        new BigDecimal("7000"), BigDecimal.ZERO, new BigDecimal("5000"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
                ),
                brackets
        );

        assertThat(result.bracketLevel()).isEqualTo(2);
        assertThat(result.currentWithholdingTax()).isEqualByComparingTo("150.00");
    }

    private CumulativeWithholdingTaxCalculator.Result calculate(String taxableIncome) {
        return CumulativeWithholdingTaxCalculator.calculate(
                CumulativeWithholdingTaxCalculator.Input.of(
                        new BigDecimal(taxableIncome), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
                )
        );
    }
}
