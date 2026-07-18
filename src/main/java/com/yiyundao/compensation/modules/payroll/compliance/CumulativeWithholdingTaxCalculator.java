package com.yiyundao.compensation.modules.payroll.compliance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 居民个人工资薪金累计预扣预缴计算器。
 *
 * <p>计算器只接收已经确定的累计口径，不读取数据库，也不改变业务状态。
 * 政策中心可以通过传入自定义税率表替换默认表；默认表仅用于首期内置政策和离线试算。</p>
 */
public final class CumulativeWithholdingTaxCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private CumulativeWithholdingTaxCalculator() {
    }

    public record TaxBracket(
            int level,
            BigDecimal upperLimit,
            BigDecimal rate,
            BigDecimal quickDeduction
    ) {
        public TaxBracket {
            if (level < 1) {
                throw new IllegalArgumentException("税率表级数必须从1开始");
            }
            Objects.requireNonNull(rate, "税率不能为空");
            Objects.requireNonNull(quickDeduction, "速算扣除数不能为空");
            if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("税率必须位于0到1之间");
            }
            if (quickDeduction.signum() < 0) {
                throw new IllegalArgumentException("速算扣除数不能为负数");
            }
        }
    }

    public record Input(
            BigDecimal cumulativeIncome,
            BigDecimal cumulativeTaxExemptIncome,
            BigDecimal cumulativeBasicDeduction,
            BigDecimal cumulativeSpecialDeduction,
            BigDecimal cumulativeSpecialAdditionalDeduction,
            BigDecimal cumulativeOtherDeduction,
            BigDecimal cumulativeTaxReduction,
            BigDecimal cumulativeWithheldTax,
            int scale,
            RoundingMode roundingMode
    ) {
        public Input {
            if (scale < 0 || scale > 8) {
                throw new IllegalArgumentException("金额精度必须位于0到8之间");
            }
            Objects.requireNonNull(roundingMode, "舍入方式不能为空");
        }

        public static Input of(
                BigDecimal cumulativeIncome,
                BigDecimal cumulativeTaxExemptIncome,
                BigDecimal cumulativeBasicDeduction,
                BigDecimal cumulativeSpecialDeduction,
                BigDecimal cumulativeSpecialAdditionalDeduction,
                BigDecimal cumulativeOtherDeduction,
                BigDecimal cumulativeTaxReduction,
                BigDecimal cumulativeWithheldTax
        ) {
            return new Input(
                    cumulativeIncome,
                    cumulativeTaxExemptIncome,
                    cumulativeBasicDeduction,
                    cumulativeSpecialDeduction,
                    cumulativeSpecialAdditionalDeduction,
                    cumulativeOtherDeduction,
                    cumulativeTaxReduction,
                    cumulativeWithheldTax,
                    2,
                    RoundingMode.HALF_UP
            );
        }
    }

    public record Result(
            BigDecimal cumulativeTaxableIncome,
            BigDecimal rate,
            BigDecimal quickDeduction,
            BigDecimal cumulativeTaxBeforeReduction,
            BigDecimal currentWithholdingTax,
            int bracketLevel,
            String formula
    ) {
    }

    /**
     * 个人所得税预扣率表一，金额单位为元，税率以小数表示。
     */
    public static List<TaxBracket> standardResidentWageBrackets() {
        return List.of(
                new TaxBracket(1, new BigDecimal("36000"), new BigDecimal("0.03"), ZERO),
                new TaxBracket(2, new BigDecimal("144000"), new BigDecimal("0.10"), new BigDecimal("2520")),
                new TaxBracket(3, new BigDecimal("300000"), new BigDecimal("0.20"), new BigDecimal("16920")),
                new TaxBracket(4, new BigDecimal("420000"), new BigDecimal("0.25"), new BigDecimal("31920")),
                new TaxBracket(5, new BigDecimal("660000"), new BigDecimal("0.30"), new BigDecimal("52920")),
                new TaxBracket(6, new BigDecimal("960000"), new BigDecimal("0.35"), new BigDecimal("85920")),
                new TaxBracket(7, null, new BigDecimal("0.45"), new BigDecimal("181920"))
        );
    }

    public static Result calculate(Input input) {
        return calculate(input, standardResidentWageBrackets());
    }

    public static Result calculate(Input input, List<TaxBracket> brackets) {
        Objects.requireNonNull(input, "累计预扣输入不能为空");
        if (brackets == null || brackets.isEmpty()) {
            throw new IllegalArgumentException("税率表不能为空");
        }

        BigDecimal income = amount(input.cumulativeIncome());
        BigDecimal taxExempt = amount(input.cumulativeTaxExemptIncome());
        BigDecimal basic = amount(input.cumulativeBasicDeduction());
        BigDecimal special = amount(input.cumulativeSpecialDeduction());
        BigDecimal specialAdditional = amount(input.cumulativeSpecialAdditionalDeduction());
        BigDecimal other = amount(input.cumulativeOtherDeduction());
        BigDecimal reduction = amount(input.cumulativeTaxReduction());
        BigDecimal withheld = amount(input.cumulativeWithheldTax());

        BigDecimal taxable = income
                .subtract(taxExempt)
                .subtract(basic)
                .subtract(special)
                .subtract(specialAdditional)
                .subtract(other);
        taxable = maxZero(taxable).setScale(input.scale(), input.roundingMode());

        TaxBracket bracket = resolveBracket(taxable, brackets);
        BigDecimal cumulativeTax = taxable.multiply(bracket.rate())
                .subtract(bracket.quickDeduction())
                .setScale(input.scale(), input.roundingMode());
        BigDecimal current = cumulativeTax
                .subtract(reduction)
                .subtract(withheld);
        current = maxZero(current).setScale(input.scale(), input.roundingMode());

        String formula = "max(0, (" + taxable.toPlainString() + " * "
                + bracket.rate().toPlainString() + " - " + bracket.quickDeduction().toPlainString()
                + ") - " + reduction.toPlainString() + " - " + withheld.toPlainString() + ")";
        return new Result(
                taxable,
                bracket.rate(),
                bracket.quickDeduction().setScale(input.scale(), input.roundingMode()),
                cumulativeTax,
                current,
                bracket.level(),
                formula
        );
    }

    private static TaxBracket resolveBracket(BigDecimal taxable, List<TaxBracket> brackets) {
        TaxBracket previous = null;
        for (TaxBracket bracket : brackets) {
            if (previous != null && bracket.level() <= previous.level()) {
                throw new IllegalArgumentException("税率表级数必须递增");
            }
            if (bracket.upperLimit() != null && bracket.upperLimit().signum() < 0) {
                throw new IllegalArgumentException("税率表上限不能为负数");
            }
            if (bracket.upperLimit() == null || taxable.compareTo(bracket.upperLimit()) <= 0) {
                return bracket;
            }
            previous = bracket;
        }
        return brackets.get(brackets.size() - 1);
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private static BigDecimal maxZero(BigDecimal value) {
        return value.signum() < 0 ? ZERO : value;
    }
}
