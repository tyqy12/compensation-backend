package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.modules.payroll.compliance.CumulativeWithholdingTaxCalculator;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollPolicyPackage;
import com.yiyundao.compensation.modules.payroll.entity.PayrollTaxBracket;
import com.yiyundao.compensation.modules.payroll.entity.PayrollTaxDeductionDeclaration;
import com.yiyundao.compensation.modules.payroll.entity.PayrollTaxLedger;
import com.yiyundao.compensation.modules.payroll.service.PayrollCumulativeTaxService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPolicyPackageService;
import com.yiyundao.compensation.modules.payroll.service.PayrollTaxBracketService;
import com.yiyundao.compensation.modules.payroll.service.PayrollTaxDeductionDeclarationService;
import com.yiyundao.compensation.modules.payroll.service.PayrollTaxLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollCumulativeTaxServiceImpl implements PayrollCumulativeTaxService {

    public static final String STANDARD_POLICY_CODE = "CN.RESIDENT_WAGE_WITHHOLDING";
    private static final BigDecimal BASIC_DEDUCTION_PER_MONTH = new BigDecimal("5000");

    private final PayrollTaxLedgerService taxLedgerService;
    private final PayrollTaxDeductionDeclarationService declarationService;
    private final PayrollPolicyPackageService policyPackageService;
    private final PayrollTaxBracketService taxBracketService;

    @Override
    public TaxComputation calculate(Long employeeId,
                                    PayrollBatch batch,
                                    BigDecimal currentIncome,
                                    BigDecimal currentSpecialDeduction,
                                    BigDecimal currentSpecialAdditionalDeduction,
                                    BigDecimal currentOtherDeduction,
                                    int scale,
                                    RoundingMode roundingMode) {
        Period period = resolvePeriod(batch);
        PayrollTaxLedger previous = taxLedgerService.findLatestBefore(
                employeeId,
                batch == null ? null : batch.getTaxWithholdingEntityId(),
                period.year(),
                period.month()
        );

        BigDecimal previousIncome = previous == null ? BigDecimal.ZERO : value(previous.getCumulativeIncome());
        BigDecimal previousExempt = previous == null ? BigDecimal.ZERO : value(previous.getCumulativeTaxExemptIncome());
        BigDecimal previousBasic = previous == null ? BigDecimal.ZERO : value(previous.getCumulativeBasicDeduction());
        BigDecimal previousSpecial = previous == null ? BigDecimal.ZERO : value(previous.getCumulativeSpecialDeduction());
        BigDecimal previousAdditional = previous == null ? BigDecimal.ZERO : value(previous.getCumulativeSpecialAdditional());
        BigDecimal previousOther = previous == null ? BigDecimal.ZERO : value(previous.getCumulativeOtherDeduction());
        BigDecimal previousReduction = previous == null ? BigDecimal.ZERO : value(previous.getCumulativeTaxReduction());
        BigDecimal previousWithheld = previous == null ? BigDecimal.ZERO : value(previous.getCumulativeWithheldTax());

        BigDecimal currentAdditionalFromDeclarations = BigDecimal.ZERO;
        BigDecimal currentOtherFromDeclarations = BigDecimal.ZERO;
        List<PayrollTaxDeductionDeclaration> declarations = declarationService.list(
                new LambdaQueryWrapper<PayrollTaxDeductionDeclaration>()
                        .eq(PayrollTaxDeductionDeclaration::getEmployeeId, employeeId)
                        .eq(PayrollTaxDeductionDeclaration::getTaxYear, period.year())
                        .eq(PayrollTaxDeductionDeclaration::getStatus, "approved")
        );
        for (PayrollTaxDeductionDeclaration declaration : declarations) {
            if (!appliesToMonth(declaration, period.year(), period.month())) {
                continue;
            }
            BigDecimal monthlyAmount = declarationAmountForMonth(declaration);
            if (monthlyAmount.signum() <= 0) {
                continue;
            }
            if ("major_medical".equalsIgnoreCase(declaration.getDeductionType())) {
                // 大病医疗只进入年度汇算资料，不进入月度累计预扣。
                continue;
            }
            if ("individual_pension".equalsIgnoreCase(declaration.getDeductionType())
                    || "other_lawful".equalsIgnoreCase(declaration.getDeductionType())) {
                currentOtherFromDeclarations = currentOtherFromDeclarations.add(monthlyAmount);
            } else {
                currentAdditionalFromDeclarations = currentAdditionalFromDeclarations.add(monthlyAmount);
            }
        }

        BigDecimal basicForCurrentMonth = previous == null
                ? BASIC_DEDUCTION_PER_MONTH.multiply(BigDecimal.valueOf(basicDeductionMonths(batch, period)))
                : BASIC_DEDUCTION_PER_MONTH;
        BigDecimal currentAdditional = value(currentSpecialAdditionalDeduction).add(currentAdditionalFromDeclarations);
        BigDecimal currentOther = value(currentOtherDeduction).add(currentOtherFromDeclarations);
        CumulativeWithholdingTaxCalculator.Input input = new CumulativeWithholdingTaxCalculator.Input(
                previousIncome.add(value(currentIncome)),
                previousExempt,
                previousBasic.add(basicForCurrentMonth),
                previousSpecial.add(value(currentSpecialDeduction)),
                previousAdditional.add(currentAdditional),
                previousOther.add(currentOther),
                previousReduction,
                previousWithheld,
                scale,
                roundingMode
        );
        PayrollPolicyPackage policy = resolvePolicy(batch, period);
        CumulativeWithholdingTaxCalculator.Result result = CumulativeWithholdingTaxCalculator.calculate(
                input,
                resolveBrackets(policy, period)
        );
        return new TaxComputation(
                period.year(),
                period.month(),
                result,
                policy == null ? "builtin:cn-resident-wage-withholding-v1" : policy.getCode() + "@" + policy.getVersionNo(),
                policy == null ? null : policy.getId(),
                new CumulativeAmounts(
                        input.cumulativeIncome(),
                        input.cumulativeTaxExemptIncome(),
                        input.cumulativeBasicDeduction(),
                        input.cumulativeSpecialDeduction(),
                        input.cumulativeSpecialAdditionalDeduction(),
                        input.cumulativeOtherDeduction(),
                        input.cumulativeTaxReduction(),
                        input.cumulativeWithheldTax()
                )
        );
    }

    @Override
    public void recordLedger(Long employeeId, PayrollBatch batch, Long payrollLineId, TaxComputation computation) {
        if (employeeId == null || batch == null || computation == null || computation.cumulativeAmounts() == null) {
            return;
        }
        PayrollTaxLedger ledger = taxLedgerService.getOne(new LambdaQueryWrapper<PayrollTaxLedger>()
                .eq(PayrollTaxLedger::getEmployeeId, employeeId)
                .eq(PayrollTaxLedger::getTaxYear, computation.taxYear())
                .eq(PayrollTaxLedger::getTaxMonth, computation.taxMonth())
                .eq(PayrollTaxLedger::getPayrollBatchId, batch.getId())
                .eq(PayrollTaxLedger::getPayrollBatchRevision, normalizeRevision(batch.getBatchRevision()))
                .last("limit 1"));
        if (ledger == null) {
            ledger = new PayrollTaxLedger();
        }
        CumulativeAmounts amounts = computation.cumulativeAmounts();
        ledger.setEmployeeId(employeeId);
        ledger.setWithholdingEntityId(batch.getTaxWithholdingEntityId());
        ledger.setTaxYear(computation.taxYear());
        ledger.setTaxMonth(computation.taxMonth());
        ledger.setPayrollBatchId(batch.getId());
        ledger.setPayrollBatchRevision(normalizeRevision(batch.getBatchRevision()));
        ledger.setPayrollLineId(payrollLineId);
        ledger.setCumulativeIncome(amounts.income());
        ledger.setCumulativeTaxExemptIncome(amounts.taxExemptIncome());
        ledger.setCumulativeBasicDeduction(amounts.basicDeduction());
        ledger.setCumulativeSpecialDeduction(amounts.specialDeduction());
        ledger.setCumulativeSpecialAdditional(amounts.specialAdditionalDeduction());
        ledger.setCumulativeOtherDeduction(amounts.otherDeduction());
        ledger.setCumulativeTaxableIncome(computation.result().cumulativeTaxableIncome());
        ledger.setTaxRate(computation.result().rate());
        ledger.setQuickDeduction(computation.result().quickDeduction());
        ledger.setCumulativeTax(computation.result().cumulativeTaxBeforeReduction());
        ledger.setCumulativeTaxReduction(amounts.taxReduction());
        ledger.setCumulativeWithheldTax(amounts.withheldTax()
                .add(value(computation.result().currentWithholdingTax())));
        ledger.setCurrentWithholdingTax(computation.result().currentWithholdingTax());
        ledger.setPolicyId(computation.policyId());
        ledger.setCalculationHash(sha256(computation.policyCode() + "|" + computation.result().formula()));
        ledger.setStatus("draft");
        taxLedgerService.saveOrUpdate(ledger);
    }

    private PayrollPolicyPackage resolvePolicy(PayrollBatch batch, Period period) {
        LambdaQueryWrapper<PayrollPolicyPackage> query = new LambdaQueryWrapper<PayrollPolicyPackage>()
                .eq(PayrollPolicyPackage::getPolicyType, "tax")
                .eq(PayrollPolicyPackage::getStatus, "published")
                .le(PayrollPolicyPackage::getEffectiveFrom, period.firstDay())
                .and(item -> item.isNull(PayrollPolicyPackage::getEffectiveTo)
                        .or().ge(PayrollPolicyPackage::getEffectiveTo, period.firstDay()))
                .orderByDesc(PayrollPolicyPackage::getVersionNo)
                .last("limit 1");
        if (batch != null && batch.getPolicyPackageId() != null) {
            query.eq(PayrollPolicyPackage::getId, batch.getPolicyPackageId());
        } else {
            query.eq(PayrollPolicyPackage::getCode, STANDARD_POLICY_CODE);
        }
        return policyPackageService.getOne(query);
    }

    private List<CumulativeWithholdingTaxCalculator.TaxBracket> resolveBrackets(
            PayrollPolicyPackage policy,
            Period period
    ) {
        if (policy == null) {
            return CumulativeWithholdingTaxCalculator.standardResidentWageBrackets();
        }
        List<PayrollTaxBracket> rows = taxBracketService.list(new LambdaQueryWrapper<PayrollTaxBracket>()
                .eq(PayrollTaxBracket::getPolicyId, policy.getId())
                .eq(PayrollTaxBracket::getTaxYear, period.year())
                .orderByAsc(PayrollTaxBracket::getBracketLevel));
        if (rows.isEmpty()) {
            return CumulativeWithholdingTaxCalculator.standardResidentWageBrackets();
        }
        return rows.stream()
                .map(row -> new CumulativeWithholdingTaxCalculator.TaxBracket(
                        row.getBracketLevel(), row.getUpperLimit(), row.getRate(), row.getQuickDeduction()))
                .sorted(Comparator.comparingInt(CumulativeWithholdingTaxCalculator.TaxBracket::level))
                .toList();
    }

    private boolean appliesToMonth(PayrollTaxDeductionDeclaration declaration, int year, int month) {
        LocalDate date = LocalDate.of(year, month, 1);
        return (declaration.getEffectiveFrom() == null || !date.isBefore(declaration.getEffectiveFrom().withDayOfMonth(1)))
                && (declaration.getEffectiveTo() == null || !date.isAfter(declaration.getEffectiveTo().withDayOfMonth(1)));
    }

    private int basicDeductionMonths(PayrollBatch batch, Period period) {
        if (batch != null && batch.getTaxBasicDeductionMonths() != null
                && batch.getTaxBasicDeductionMonths() > 0) {
            return batch.getTaxBasicDeductionMonths();
        }
        // 没有历史台账时只能确认本单位本次任职月份；跨年/月中入职不能臆造年初累计月份。
        // 如企业已经完成历史迁移，应在批次上明确填写 taxBasicDeductionMonths。
        return 1;
    }

    private BigDecimal declarationAmountForMonth(PayrollTaxDeductionDeclaration declaration) {
        BigDecimal amount = declaration.getMonthlyAmount();
        if (amount == null && declaration.getAnnualAmount() != null) {
            amount = declaration.getAnnualAmount().divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
        }
        BigDecimal ratio = declaration.getAllocationRatio() == null
                ? BigDecimal.ONE
                : declaration.getAllocationRatio();
        return value(amount).multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    private Period resolvePeriod(PayrollBatch batch) {
        if (batch != null && batch.getTaxYear() != null && batch.getTaxMonth() != null) {
            return new Period(batch.getTaxYear(), batch.getTaxMonth());
        }
        if (batch != null && batch.getPayDate() != null) {
            return new Period(batch.getPayDate().getYear(), batch.getPayDate().getMonthValue());
        }
        String label = batch == null ? null : batch.getPeriodLabel();
        if (StringUtils.hasText(label) && label.length() >= 7) {
            try {
                return new Period(Integer.parseInt(label.substring(0, 4)), Integer.parseInt(label.substring(5, 7)));
            } catch (NumberFormatException ignored) {
                // Fall through to the current system date for legacy batches with malformed labels.
            }
        }
        LocalDate now = LocalDate.now();
        return new Period(now.getYear(), now.getMonthValue());
    }

    private String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法生成个税计算摘要", e);
        }
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int normalizeRevision(Integer revision) {
        return revision == null || revision < 1 ? 1 : revision;
    }

    private record Period(int year, int month) {
        private LocalDate firstDay() {
            return LocalDate.of(year, month, 1);
        }
    }
}
