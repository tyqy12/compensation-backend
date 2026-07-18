package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.PayrollTaxDeductionDeclarationMapper;
import com.yiyundao.compensation.modules.payroll.compliance.PayrollDeductionType;
import com.yiyundao.compensation.modules.payroll.entity.PayrollTaxDeductionDeclaration;
import com.yiyundao.compensation.modules.payroll.service.PayrollTaxDeductionDeclarationService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollPolicyPackage;
import com.yiyundao.compensation.modules.payroll.service.PayrollPolicyPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollTaxDeductionDeclarationServiceImpl
        extends ServiceImpl<PayrollTaxDeductionDeclarationMapper, PayrollTaxDeductionDeclaration>
        implements PayrollTaxDeductionDeclarationService {

    private static final String STANDARD_POLICY_CODE = "CN.RESIDENT_WAGE_WITHHOLDING";

    private final PayrollPolicyPackageService policyPackageService;
    private final ObjectMapper objectMapper;

    @Override
    public PayrollTaxDeductionDeclaration saveValidated(PayrollTaxDeductionDeclaration declaration) {
        if (declaration == null || declaration.getEmployeeId() == null || declaration.getTaxYear() == null
                || !PayrollDeductionType.supported(declaration.getDeductionType())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "员工、年度和有效扣除类型不能为空");
        }
        if (declaration.getAllocationRatio() == null
                || declaration.getAllocationRatio().signum() < 0
                || declaration.getAllocationRatio().compareTo(java.math.BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "扣除分配比例必须位于0到1之间");
        }
        PayrollPolicyPackage policy = resolvePolicy(declaration.getTaxYear());
        if (policy == null || !org.springframework.util.StringUtils.hasText(policy.getPayloadJson())) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "缺少已发布的居民工资薪金扣除政策版本，暂不能受理扣除申报");
        }
        declaration.setPolicyId(policy.getId());
        validateEvidence(declaration);
        validateFacts(declaration);
        applyDerivedMonthlyAmount(declaration, policy);
        validatePolicyAmount(declaration, policy);
        LocalDate from = declaration.getEffectiveFrom();
        LocalDate to = declaration.getEffectiveTo();
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "扣除有效期结束日不能早于开始日");
        }
        if ("major_medical".equalsIgnoreCase(declaration.getDeductionType())
                && declaration.getMonthlyAmount() != null
                && declaration.getMonthlyAmount().signum() > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "大病医疗属于年度汇算扣除，不能作为月度预扣金额");
        }
        List<PayrollTaxDeductionDeclaration> existing = list(new LambdaQueryWrapper<PayrollTaxDeductionDeclaration>()
                .eq(PayrollTaxDeductionDeclaration::getEmployeeId, declaration.getEmployeeId())
                .eq(PayrollTaxDeductionDeclaration::getTaxYear, declaration.getTaxYear())
                .in(PayrollTaxDeductionDeclaration::getStatus, "pending", "approved")
                .ne(declaration.getId() != null, PayrollTaxDeductionDeclaration::getId, declaration.getId()));
        if ("individual_pension".equalsIgnoreCase(declaration.getDeductionType())) {
            BigDecimal annualLimit = annualLimit(policy, declaration.getDeductionType(), new BigDecimal("12000"));
            java.math.BigDecimal annualTotal = existing.stream()
                    .filter(item -> "individual_pension".equalsIgnoreCase(item.getDeductionType()))
                    .map(this::annualizedAmount)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                    .add(annualizedAmount(declaration));
            if (annualTotal.compareTo(annualLimit) > 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "个人养老金年度扣除累计不得超过" + annualLimit.stripTrailingZeros().toPlainString() + "元");
            }
        }
        if ("housing_loan_interest".equalsIgnoreCase(declaration.getDeductionType())
                || "rent".equalsIgnoreCase(declaration.getDeductionType())) {
            String mutuallyExclusive = "housing_loan_interest".equalsIgnoreCase(declaration.getDeductionType())
                    ? "rent" : "housing_loan_interest";
            boolean exists = existing.stream()
                    .filter(item -> mutuallyExclusive.equalsIgnoreCase(item.getDeductionType()))
                    .anyMatch(item -> overlaps(from, to, item.getEffectiveFrom(), item.getEffectiveTo()));
            if (exists) {
                throw new BusinessException(ErrorCode.REQUEST_CONFLICT, "住房贷款利息和住房租金不能在同一期间同时申报");
            }
        }
        if (declaration.getStatus() == null || declaration.getStatus().isBlank()) {
            declaration.setStatus("pending");
        }
        saveOrUpdate(declaration);
        return declaration;
    }

    @Override
    public PayrollTaxDeductionDeclaration approveValidated(Long declarationId) {
        PayrollTaxDeductionDeclaration declaration = getById(declarationId);
        if (declaration == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "扣除申报不存在");
        }
        if (!"pending".equalsIgnoreCase(declaration.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "只有待审核扣除申报可以批准");
        }
        PayrollPolicyPackage policy = declaration.getPolicyId() == null
                ? resolvePolicy(declaration.getTaxYear())
                : policyPackageService.getById(declaration.getPolicyId());
        if (policy == null || !"published".equalsIgnoreCase(policy.getStatus())) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "扣除申报引用的政策版本不可用，暂不能批准");
        }
        validateEvidence(declaration);
        validateFacts(declaration);
        applyDerivedMonthlyAmount(declaration, policy);
        validatePolicyAmount(declaration, policy);
        declaration.setPolicyId(policy.getId());
        declaration.setStatus("approved");
        updateById(declaration);
        return declaration;
    }

    private PayrollPolicyPackage resolvePolicy(Integer taxYear) {
        LocalDate yearStart = LocalDate.of(taxYear, 1, 1);
        return policyPackageService.getOne(new LambdaQueryWrapper<PayrollPolicyPackage>()
                .eq(PayrollPolicyPackage::getCode, STANDARD_POLICY_CODE)
                .eq(PayrollPolicyPackage::getPolicyType, "tax")
                .eq(PayrollPolicyPackage::getStatus, "published")
                .le(PayrollPolicyPackage::getEffectiveFrom, yearStart)
                .and(item -> item.isNull(PayrollPolicyPackage::getEffectiveTo)
                        .or().ge(PayrollPolicyPackage::getEffectiveTo, yearStart))
                .orderByDesc(PayrollPolicyPackage::getVersionNo)
                .last("limit 1"));
    }

    private void validateEvidence(PayrollTaxDeductionDeclaration declaration) {
        if (!org.springframework.util.StringUtils.hasText(declaration.getEvidenceJson())) {
            return;
        }
        try {
            objectMapper.readTree(declaration.getEvidenceJson());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "扣除凭证元数据必须是合法 JSON");
        }
    }

    private void validateFacts(PayrollTaxDeductionDeclaration declaration) {
        if ("other_lawful".equalsIgnoreCase(declaration.getDeductionType())) {
            return;
        }
        if (!org.springframework.util.StringUtils.hasText(declaration.getFactsJson())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "专项附加扣除必须提交事实信息");
        }
        try {
            JsonNode facts = objectMapper.readTree(declaration.getFactsJson());
            if (facts == null || !facts.isObject()) {
                throw new IllegalArgumentException("facts must be an object");
            }
            if ("rent".equalsIgnoreCase(declaration.getDeductionType())
                    && !facts.hasNonNull("cityLevel") && !facts.hasNonNull("city")) {
                throw new IllegalArgumentException("rent city is required");
            }
            if (("infant_care".equalsIgnoreCase(declaration.getDeductionType())
                    || "child_education".equalsIgnoreCase(declaration.getDeductionType()))
                    && !org.springframework.util.StringUtils.hasText(declaration.getSubjectKey())) {
                throw new IllegalArgumentException("subject key is required");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "扣除事实信息必须是合法且完整的 JSON");
        }
    }

    private void validatePolicyAmount(PayrollTaxDeductionDeclaration declaration,
                                      PayrollPolicyPackage policy) {
        JsonNode rule;
        try {
            rule = objectMapper.readTree(policy.getPayloadJson())
                    .path("deductions")
                    .path(declaration.getDeductionType().toLowerCase());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "扣除政策参数 JSON 无法解析");
        }
        if (rule.isMissingNode() || rule.isNull()) {
            if (!"other_lawful".equalsIgnoreCase(declaration.getDeductionType())) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                        "扣除政策未配置类型：" + declaration.getDeductionType());
            }
            return;
        }
        BigDecimal maxMonthly = firstDecimal(rule, "monthlyAmount", "monthlyPerSubject", "singleChildMonthlyAmount");
        JsonNode facts = readFacts(declaration);
        if (("infant_care".equalsIgnoreCase(declaration.getDeductionType())
                || "child_education".equalsIgnoreCase(declaration.getDeductionType()))
                && maxMonthly != null) {
            int subjectCount = Math.max(1, facts.path("subjectCount").asInt(1));
            BigDecimal ratio = declaration.getAllocationRatio() == null
                    ? BigDecimal.ONE : declaration.getAllocationRatio();
            maxMonthly = maxMonthly.multiply(BigDecimal.valueOf(subjectCount)).multiply(ratio);
        }
        if ("elderly_care".equalsIgnoreCase(declaration.getDeductionType())
                && facts.has("isOnlyChild") && !facts.path("isOnlyChild").asBoolean()) {
            maxMonthly = firstDecimal(rule, "perPersonLimit");
        }
        if ("rent".equalsIgnoreCase(declaration.getDeductionType()) && rule.has("monthlyAmounts")) {
            for (JsonNode amount : rule.path("monthlyAmounts")) {
                if (amount.isNumber()
                        && (maxMonthly == null || amount.decimalValue().compareTo(maxMonthly) > 0)) {
                    maxMonthly = amount.decimalValue();
                }
            }
            if (declaration.getMonthlyAmount() != null && !isOneOf(declaration.getMonthlyAmount(), rule.path("monthlyAmounts"))) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "住房租金金额不匹配所在城市政策档位");
            }
        }
        BigDecimal maxAnnual = firstDecimal(rule, "annualLimit", "vocationalAnnualAmount");
        if (maxAnnual == null && maxMonthly != null) {
            maxAnnual = maxMonthly.multiply(new BigDecimal("12"));
        }
        if (declaration.getMonthlyAmount() != null && maxMonthly != null
                && declaration.getMonthlyAmount().compareTo(maxMonthly) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "月度扣除金额超过政策上限" + maxMonthly.stripTrailingZeros().toPlainString() + "元");
        }
        if (declaration.getAnnualAmount() != null && maxAnnual != null
                && declaration.getAnnualAmount().compareTo(maxAnnual) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "年度扣除金额超过政策上限" + maxAnnual.stripTrailingZeros().toPlainString() + "元");
        }
        if (declaration.getMonthlyAmount() != null && declaration.getAnnualAmount() != null
                && declaration.getAnnualAmount().compareTo(declaration.getMonthlyAmount().multiply(new BigDecimal("12"))) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "月度和年度扣除金额口径不一致");
        }
    }

    private BigDecimal annualLimit(PayrollPolicyPackage policy, String type, BigDecimal fallback) {
        try {
            BigDecimal value = objectMapper.readTree(policy.getPayloadJson())
                    .path("deductions").path(type).path("annualLimit").decimalValue();
            return value.signum() > 0 ? value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void applyDerivedMonthlyAmount(PayrollTaxDeductionDeclaration declaration,
                                           PayrollPolicyPackage policy) {
        if (declaration.getMonthlyAmount() != null || "major_medical".equalsIgnoreCase(declaration.getDeductionType())
                || "individual_pension".equalsIgnoreCase(declaration.getDeductionType())) {
            return;
        }
        JsonNode facts = readFacts(declaration);
        try {
            JsonNode rule = objectMapper.readTree(policy.getPayloadJson())
                    .path("deductions").path(declaration.getDeductionType().toLowerCase());
            BigDecimal amount = null;
            String type = declaration.getDeductionType().toLowerCase();
            if ("infant_care".equals(type) || "child_education".equals(type)) {
                amount = firstDecimal(rule, "monthlyPerSubject");
                int count = Math.max(1, facts.path("subjectCount").asInt(1));
                amount = amount.multiply(BigDecimal.valueOf(count));
            } else if ("elderly_care".equals(type)) {
                amount = facts.path("isOnlyChild").asBoolean(false)
                        ? firstDecimal(rule, "singleChildMonthlyAmount")
                        : firstDecimal(rule, "nonSingleTotalMonthlyAmount");
            } else if ("rent".equals(type)) {
                amount = decimalField(facts, "monthlyAmount");
                if (amount == null && facts.has("cityLevel")) {
                    JsonNode amounts = rule.path("monthlyAmounts");
                    int level = facts.path("cityLevel").asInt(3);
                    int index = level <= 1 ? 2 : level == 2 ? 1 : 0;
                    if (amounts.isArray() && amounts.size() > index && amounts.get(index).isNumber()) {
                        amount = amounts.get(index).decimalValue();
                    }
                }
            } else {
                amount = firstDecimal(rule, "monthlyAmount");
            }
            if (amount != null) {
                BigDecimal ratio = declaration.getAllocationRatio() == null
                        ? BigDecimal.ONE : declaration.getAllocationRatio();
                declaration.setMonthlyAmount(amount.multiply(ratio).setScale(2, java.math.RoundingMode.HALF_UP));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "无法根据政策推导扣除金额");
        }
    }

    private JsonNode readFacts(PayrollTaxDeductionDeclaration declaration) {
        try {
            return objectMapper.readTree(declaration.getFactsJson());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "扣除事实信息格式无效");
        }
    }

    private BigDecimal decimalField(JsonNode node, String field) {
        return node.has(field) && node.get(field).isNumber() ? node.get(field).decimalValue() : null;
    }

    private boolean isOneOf(BigDecimal value, JsonNode values) {
        for (JsonNode item : values) {
            if (item.isNumber() && item.decimalValue().compareTo(value) == 0) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal firstDecimal(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && node.get(field).isNumber()) {
                return node.get(field).decimalValue();
            }
        }
        return null;
    }

    private java.math.BigDecimal annualizedAmount(PayrollTaxDeductionDeclaration declaration) {
        if (declaration == null) {
            return java.math.BigDecimal.ZERO;
        }
        if (declaration.getAnnualAmount() != null) {
            return declaration.getAnnualAmount();
        }
        if (declaration.getMonthlyAmount() == null) {
            return java.math.BigDecimal.ZERO;
        }
        return declaration.getMonthlyAmount().multiply(new java.math.BigDecimal("12"));
    }

    private boolean overlaps(LocalDate leftFrom,
                             LocalDate leftTo,
                             LocalDate rightFrom,
                             LocalDate rightTo) {
        LocalDate leftStart = leftFrom == null ? LocalDate.MIN : leftFrom;
        LocalDate leftEnd = leftTo == null ? LocalDate.MAX : leftTo;
        LocalDate rightStart = rightFrom == null ? LocalDate.MIN : rightFrom;
        LocalDate rightEnd = rightTo == null ? LocalDate.MAX : rightTo;
        return !leftStart.isAfter(rightEnd) && !rightStart.isAfter(leftEnd);
    }
}
