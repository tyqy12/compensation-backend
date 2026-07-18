package com.yiyundao.compensation.modules.payroll.compliance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 符合条件的全年一次性奖金单独计税试算。是否符合条件由业务审核，不由“年度”字样自动推断。 */
public final class AnnualBonusTaxCalculator {

    private AnnualBonusTaxCalculator() {
    }

    public record Result(
            BigDecimal monthlyMatch,
            BigDecimal rate,
            BigDecimal quickDeduction,
            BigDecimal taxAmount,
            int bracketLevel,
            String formula
    ) {
    }

    private record Bracket(int level, BigDecimal upperLimit, BigDecimal rate, BigDecimal quickDeduction) {
    }

    private static final List<Bracket> BRACKETS = List.of(
            new Bracket(1, new BigDecimal("3000"), new BigDecimal("0.03"), BigDecimal.ZERO),
            new Bracket(2, new BigDecimal("12000"), new BigDecimal("0.10"), new BigDecimal("210")),
            new Bracket(3, new BigDecimal("25000"), new BigDecimal("0.20"), new BigDecimal("1410")),
            new Bracket(4, new BigDecimal("35000"), new BigDecimal("0.25"), new BigDecimal("2660")),
            new Bracket(5, new BigDecimal("55000"), new BigDecimal("0.30"), new BigDecimal("4410")),
            new Bracket(6, new BigDecimal("80000"), new BigDecimal("0.35"), new BigDecimal("7160")),
            new Bracket(7, null, new BigDecimal("0.45"), new BigDecimal("15160"))
    );

    public static Result calculate(BigDecimal annualBonus) {
        if (annualBonus == null || annualBonus.signum() < 0) {
            throw new IllegalArgumentException("全年一次性奖金不能为负数");
        }
        BigDecimal monthly = annualBonus.divide(new BigDecimal("12"), 10, RoundingMode.HALF_UP);
        Bracket bracket = BRACKETS.stream()
                .filter(item -> item.upperLimit() == null || monthly.compareTo(item.upperLimit()) <= 0)
                .findFirst()
                .orElse(BRACKETS.get(BRACKETS.size() - 1));
        BigDecimal tax = annualBonus.multiply(bracket.rate())
                .subtract(bracket.quickDeduction())
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        return new Result(
                monthly,
                bracket.rate(),
                bracket.quickDeduction(),
                tax,
                bracket.level(),
                annualBonus.toPlainString() + " * " + bracket.rate().toPlainString()
                        + " - " + bracket.quickDeduction().toPlainString()
        );
    }
}
