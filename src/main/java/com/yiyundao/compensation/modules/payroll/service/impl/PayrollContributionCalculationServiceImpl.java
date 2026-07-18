package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.modules.payroll.compliance.ContributionCalculator;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollContributionPolicy;
import com.yiyundao.compensation.modules.payroll.entity.PayrollEnrollment;
import com.yiyundao.compensation.modules.payroll.service.PayrollContributionCalculationService;
import com.yiyundao.compensation.modules.payroll.service.PayrollContributionPolicyService;
import com.yiyundao.compensation.modules.payroll.service.PayrollEnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollContributionCalculationServiceImpl implements PayrollContributionCalculationService {

    private final PayrollEnrollmentService enrollmentService;
    private final PayrollContributionPolicyService policyService;

    @Override
    public Result calculate(Long employeeId,
                            PayrollBatch batch,
                            BigDecimal declaredWage,
                            BigDecimal fallbackEmployeeAmount,
                            RoundingMode fallbackRoundingMode) {
        LocalDate asOf = resolveDate(batch);
        List<PayrollEnrollment> enrollments = enrollmentService.list(new LambdaQueryWrapper<PayrollEnrollment>()
                .eq(PayrollEnrollment::getEmployeeId, employeeId)
                .eq(PayrollEnrollment::getStatus, "active")
                .orderByAsc(PayrollEnrollment::getContributionType)
                .orderByDesc(PayrollEnrollment::getPrimaryFlag));
        List<PayrollEnrollment> applicable = enrollments.stream()
                .filter(item -> appliesTo(item, asOf))
                .toList();
        if (applicable.isEmpty()) {
            return fallback(fallbackEmployeeAmount);
        }

        List<Line> lines = new ArrayList<>();
        BigDecimal employeeTotal = BigDecimal.ZERO;
        BigDecimal employerTotal = BigDecimal.ZERO;
        for (PayrollEnrollment enrollment : applicable) {
            PayrollContributionPolicy policy = resolvePolicy(enrollment, asOf);
            if (policy == null) {
                // 缺少任一险种的地区政策时整体回退，避免部分采用地方口径、部分采用模板比例。
                return fallback(fallbackEmployeeAmount);
            }
            RoundingMode roundingMode = resolveRoundingMode(policy.getRoundingMode(), fallbackRoundingMode);
            ContributionCalculator.Result calculation = ContributionCalculator.calculate(
                    declaredWage,
                    new ContributionCalculator.Policy(
                            enrollment.getContributionType(),
                            policy.getBaseMin(),
                            policy.getBaseMax(),
                            policy.getEmployerRate(),
                            policy.getEmployeeRate(),
                            policy.getEmployerFixedAmount(),
                            policy.getEmployeeFixedAmount(),
                            roundingMode
                    )
            );
            BigDecimal employerAmount = applyMinimum(calculation.employerAmount(), policy.getMinimumAmount());
            BigDecimal employeeAmount = applyMinimum(calculation.employeeAmount(), policy.getMinimumAmount());
            lines.add(new Line(
                    enrollment.getContributionType(),
                    enrollment.getRegionCode(),
                    policy.getId(),
                    calculation.declaredWage(),
                    calculation.contributionBase(),
                    policy.getEmployerRate(),
                    policy.getEmployeeRate(),
                    employerAmount,
                    employeeAmount,
                    calculation.formula(),
                    sha256(calculation.formula() + "|" + policy.getId())
            ));
            employeeTotal = employeeTotal.add(employeeAmount);
            employerTotal = employerTotal.add(employerAmount);
        }
        return new Result(lines, employeeTotal, employerTotal, true);
    }

    private PayrollContributionPolicy resolvePolicy(PayrollEnrollment enrollment, LocalDate asOf) {
        LambdaQueryWrapper<PayrollContributionPolicy> query = new LambdaQueryWrapper<PayrollContributionPolicy>()
                .eq(PayrollContributionPolicy::getRegionCode, enrollment.getRegionCode())
                .eq(PayrollContributionPolicy::getContributionType, enrollment.getContributionType())
                .eq(PayrollContributionPolicy::getStatus, "published")
                .le(PayrollContributionPolicy::getEffectiveFrom, asOf)
                .and(item -> item.isNull(PayrollContributionPolicy::getEffectiveTo)
                        .or().ge(PayrollContributionPolicy::getEffectiveTo, asOf))
                .orderByDesc(PayrollContributionPolicy::getVersionNo);
        if (enrollment.getPolicyId() != null) {
            query.eq(PayrollContributionPolicy::getId, enrollment.getPolicyId());
        }
        if (enrollment.getCollectionEntityCode() == null || enrollment.getCollectionEntityCode().isBlank()) {
            query.isNull(PayrollContributionPolicy::getCollectionEntityCode);
        } else {
            query.and(item -> {
                item.isNull(PayrollContributionPolicy::getCollectionEntityCode)
                        .or().eq(PayrollContributionPolicy::getCollectionEntityCode, enrollment.getCollectionEntityCode());
            });
        }
        List<PayrollContributionPolicy> candidates = policyService.list(query);
        return candidates.stream()
                .sorted(Comparator
                        .comparing((PayrollContributionPolicy item) ->
                                        enrollment.getCollectionEntityCode() != null
                                                && enrollment.getCollectionEntityCode().equals(item.getCollectionEntityCode()))
                        .reversed()
                        .thenComparing(item -> item.getVersionNo() == null ? 0L : item.getVersionNo(), Comparator.reverseOrder()))
                .findFirst()
                .orElse(null);
    }

    private boolean appliesTo(PayrollEnrollment enrollment, LocalDate asOf) {
        return enrollment.getEffectiveFrom() != null
                && !asOf.isBefore(enrollment.getEffectiveFrom())
                && (enrollment.getEffectiveTo() == null || !asOf.isAfter(enrollment.getEffectiveTo()));
    }

    private LocalDate resolveDate(PayrollBatch batch) {
        if (batch != null && batch.getPayDate() != null) {
            return batch.getPayDate();
        }
        if (batch != null && batch.getTaxYear() != null && batch.getTaxMonth() != null) {
            return LocalDate.of(batch.getTaxYear(), batch.getTaxMonth(), 1);
        }
        if (batch != null && batch.getPeriodLabel() != null && batch.getPeriodLabel().length() >= 7) {
            try {
                return LocalDate.of(
                        Integer.parseInt(batch.getPeriodLabel().substring(0, 4)),
                        Integer.parseInt(batch.getPeriodLabel().substring(5, 7)),
                        1
                );
            } catch (RuntimeException ignored) {
                // Fall through to the current date for legacy period labels.
            }
        }
        return LocalDate.now();
    }

    private Result fallback(BigDecimal fallbackEmployeeAmount) {
        return new Result(List.of(), value(fallbackEmployeeAmount), BigDecimal.ZERO, false);
    }

    private BigDecimal applyMinimum(BigDecimal amount, BigDecimal minimumAmount) {
        if (minimumAmount == null || amount.compareTo(minimumAmount) >= 0) {
            return amount;
        }
        return minimumAmount;
    }

    private RoundingMode resolveRoundingMode(String configured, RoundingMode fallback) {
        if (configured != null && !configured.isBlank()) {
            try {
                return RoundingMode.valueOf(configured.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Use the template rounding rule when the policy data is invalid.
            }
        }
        return fallback == null ? RoundingMode.HALF_UP : fallback;
    }

    private BigDecimal value(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法生成缴费计算摘要", e);
        }
    }
}
