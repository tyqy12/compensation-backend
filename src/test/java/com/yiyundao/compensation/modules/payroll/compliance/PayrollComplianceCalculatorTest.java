package com.yiyundao.compensation.modules.payroll.compliance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollComplianceCalculatorTest {

    @Test
    void shouldCalculateAnnualBonusWithMonthlyBracket() {
        AnnualBonusTaxCalculator.Result result = AnnualBonusTaxCalculator.calculate(new BigDecimal("12000"));

        assertThat(result.monthlyMatch()).isEqualByComparingTo("1000.0000000000");
        assertThat(result.rate()).isEqualByComparingTo("0.03");
        assertThat(result.taxAmount()).isEqualByComparingTo("360.00");
    }

    @Test
    void shouldClampContributionBaseAndKeepEmployerAndEmployeeSeparate() {
        ContributionCalculator.Result result = ContributionCalculator.calculate(
                new BigDecimal("30000"),
                new ContributionCalculator.Policy(
                        "pension", new BigDecimal("5000"), new BigDecimal("20000"),
                        new BigDecimal("0.16"), new BigDecimal("0.08"),
                        BigDecimal.ZERO, BigDecimal.ZERO, RoundingMode.HALF_UP
                )
        );

        assertThat(result.contributionBase()).isEqualByComparingTo("20000.00");
        assertThat(result.employerAmount()).isEqualByComparingTo("3200.00");
        assertThat(result.employeeAmount()).isEqualByComparingTo("1600.00");
    }

    @Test
    void shouldUseTheThreeStatutoryOvertimeMultipliers() {
        assertThat(OvertimePayCalculator.calculate(new BigDecimal("100"), new BigDecimal("2"), OvertimePayCalculator.OvertimeType.WORKDAY_EXTENSION))
                .isEqualByComparingTo("300.00");
        assertThat(OvertimePayCalculator.calculate(new BigDecimal("100"), new BigDecimal("2"), OvertimePayCalculator.OvertimeType.REST_DAY_WITHOUT_COMPENSATORY_LEAVE))
                .isEqualByComparingTo("400.00");
        assertThat(OvertimePayCalculator.calculate(new BigDecimal("100"), new BigDecimal("2"), OvertimePayCalculator.OvertimeType.STATUTORY_HOLIDAY))
                .isEqualByComparingTo("600.00");
    }
}
