package com.yiyundao.compensation.modules.payroll.compliance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** 五险一金单险种基数和单位/个人金额计算器，所有地方参数由政策对象传入。 */
public final class ContributionCalculator {

    private ContributionCalculator() {
    }

    public record Policy(
            String contributionType,
            BigDecimal baseMin,
            BigDecimal baseMax,
            BigDecimal employerRate,
            BigDecimal employeeRate,
            BigDecimal employerFixedAmount,
            BigDecimal employeeFixedAmount,
            RoundingMode roundingMode
    ) {
        public Policy {
            Objects.requireNonNull(contributionType, "险种不能为空");
            Objects.requireNonNull(roundingMode, "舍入方式不能为空");
        }
    }

    public record Result(
            BigDecimal declaredWage,
            BigDecimal contributionBase,
            BigDecimal employerAmount,
            BigDecimal employeeAmount,
            String formula
    ) {
    }

    public static Result calculate(BigDecimal declaredWage, Policy policy) {
        BigDecimal wage = value(declaredWage);
        BigDecimal base = wage;
        if (policy.baseMin() != null && base.compareTo(policy.baseMin()) < 0) {
            base = policy.baseMin();
        }
        if (policy.baseMax() != null && base.compareTo(policy.baseMax()) > 0) {
            base = policy.baseMax();
        }
        BigDecimal employer = base.multiply(value(policy.employerRate()))
                .add(value(policy.employerFixedAmount()))
                .setScale(2, policy.roundingMode());
        BigDecimal employee = base.multiply(value(policy.employeeRate()))
                .add(value(policy.employeeFixedAmount()))
                .setScale(2, policy.roundingMode());
        return new Result(
                wage.setScale(2, policy.roundingMode()),
                base.setScale(2, policy.roundingMode()),
                employer,
                employee,
                "base=clamp(" + wage.toPlainString() + ", min, max); amount=base*rate+fixed"
        );
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
