package com.yiyundao.compensation.interfaces.controller.payroll;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollEnrollmentRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollEnrollmentResponse;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollAnnualBonusPreviewRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollContributionPreviewRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollContributionPolicyRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollTaxDeductionDeclarationRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollTaxPreviewRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPolicyPackageRequest;
import com.yiyundao.compensation.modules.payroll.compliance.CumulativeWithholdingTaxCalculator;
import com.yiyundao.compensation.modules.payroll.compliance.AnnualBonusTaxCalculator;
import com.yiyundao.compensation.modules.payroll.compliance.ContributionCalculator;
import com.yiyundao.compensation.modules.payroll.compliance.PayrollDeductionType;
import com.yiyundao.compensation.modules.payroll.entity.PayrollCalculationTrace;
import com.yiyundao.compensation.modules.payroll.entity.PayrollContributionPolicy;
import com.yiyundao.compensation.modules.payroll.entity.PayrollEnrollment;
import com.yiyundao.compensation.modules.payroll.entity.PayrollPolicyPackage;
import com.yiyundao.compensation.modules.payroll.entity.PayrollTaxDeductionDeclaration;
import com.yiyundao.compensation.modules.payroll.entity.PayrollTaxLedger;
import com.yiyundao.compensation.modules.payroll.service.PayrollCalculationTraceService;
import com.yiyundao.compensation.modules.payroll.service.PayrollContributionPolicyService;
import com.yiyundao.compensation.modules.payroll.service.PayrollEnrollmentService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPolicyPackageService;
import com.yiyundao.compensation.modules.payroll.service.PayrollTaxDeductionDeclarationService;
import com.yiyundao.compensation.modules.payroll.service.PayrollTaxLedgerService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/payroll/compliance")
@SecurityAnnotations.IsFinanceOrAdmin
@RequiredArgsConstructor
public class PayrollComplianceController {

    private final PayrollPolicyPackageService policyPackageService;
    private final PayrollTaxDeductionDeclarationService declarationService;
    private final PayrollTaxLedgerService taxLedgerService;
    private final PayrollEnrollmentService enrollmentService;
    private final PayrollCalculationTraceService traceService;
    private final PayrollContributionPolicyService contributionPolicyService;
    private final SysUserService sysUserService;

    @GetMapping("/tax/brackets")
    public ApiResponse<List<CumulativeWithholdingTaxCalculator.TaxBracket>> taxBrackets() {
        return ApiResponse.success(CumulativeWithholdingTaxCalculator.standardResidentWageBrackets());
    }

    @PostMapping("/tax/preview")
    public ApiResponse<CumulativeWithholdingTaxCalculator.Result> taxPreview(
            @Valid @RequestBody PayrollTaxPreviewRequest request) {
        CumulativeWithholdingTaxCalculator.Input input = new CumulativeWithholdingTaxCalculator.Input(
                request.getCumulativeIncome(),
                request.getCumulativeTaxExemptIncome(),
                request.getCumulativeBasicDeduction(),
                request.getCumulativeSpecialDeduction(),
                request.getCumulativeSpecialAdditionalDeduction(),
                request.getCumulativeOtherDeduction(),
                request.getCumulativeTaxReduction(),
                request.getCumulativeWithheldTax(),
                request.getScale(),
                request.getRoundingMode()
        );
        return ApiResponse.success(CumulativeWithholdingTaxCalculator.calculate(input));
    }

    @PostMapping("/tax/annual-bonus-preview")
    public ApiResponse<AnnualBonusTaxCalculator.Result> annualBonusPreview(
            @Valid @RequestBody PayrollAnnualBonusPreviewRequest request) {
        return ApiResponse.success(AnnualBonusTaxCalculator.calculate(request.getAnnualBonus()));
    }

    @PostMapping("/contributions/preview")
    public ApiResponse<ContributionCalculator.Result> contributionPreview(
            @Valid @RequestBody PayrollContributionPreviewRequest request) {
        ContributionCalculator.Policy policy = new ContributionCalculator.Policy(
                request.getContributionType(),
                request.getBaseMin(),
                request.getBaseMax(),
                request.getEmployerRate(),
                request.getEmployeeRate(),
                request.getEmployerFixedAmount(),
                request.getEmployeeFixedAmount(),
                request.getRoundingMode()
        );
        return ApiResponse.success(ContributionCalculator.calculate(request.getDeclaredWage(), policy));
    }

    @GetMapping("/contribution-policies")
    public ApiResponse<List<PayrollContributionPolicy>> contributionPolicies(
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String contributionType,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(contributionPolicyService.list(new LambdaQueryWrapper<PayrollContributionPolicy>()
                .eq(regionCode != null && !regionCode.isBlank(), PayrollContributionPolicy::getRegionCode, regionCode)
                .eq(contributionType != null && !contributionType.isBlank(), PayrollContributionPolicy::getContributionType, contributionType)
                .eq(status != null && !status.isBlank(), PayrollContributionPolicy::getStatus, status)
                .orderByDesc(PayrollContributionPolicy::getEffectiveFrom)
                .orderByDesc(PayrollContributionPolicy::getVersionNo)));
    }

    @PostMapping("/contribution-policies")
    public ApiResponse<PayrollContributionPolicy> createContributionPolicy(
            @Valid @RequestBody PayrollContributionPolicyRequest request) {
        PayrollContributionPolicy policy = new PayrollContributionPolicy();
        policy.setCode(request.getCode());
        policy.setRegionCode(request.getRegionCode());
        policy.setCollectionEntityCode(request.getCollectionEntityCode());
        policy.setContributionType(request.getContributionType());
        policy.setPersonCategory(request.getPersonCategory());
        policy.setHouseholdType(request.getHouseholdType());
        policy.setIndustryRiskLevel(request.getIndustryRiskLevel());
        policy.setEffectiveFrom(request.getEffectiveFrom());
        policy.setEffectiveTo(request.getEffectiveTo());
        policy.setBaseMin(request.getBaseMin());
        policy.setBaseMax(request.getBaseMax());
        policy.setEmployerRate(request.getEmployerRate());
        policy.setEmployeeRate(request.getEmployeeRate());
        policy.setEmployerFixedAmount(request.getEmployerFixedAmount());
        policy.setEmployeeFixedAmount(request.getEmployeeFixedAmount());
        policy.setRoundingMode(request.getRoundingMode());
        policy.setMinimumAmount(request.getMinimumAmount());
        policy.setSourceDocument(request.getSourceDocument());
        policy.setSourceUrl(request.getSourceUrl());
        policy.setVersionNo(request.getVersionNo());
        policy.setStatus("draft");
        return ApiResponse.success(contributionPolicyService.saveValidated(policy));
    }

    @PostMapping("/contribution-policies/{id}/review")
    public ApiResponse<PayrollContributionPolicy> reviewContributionPolicy(@PathVariable Long id) {
        Long reviewerId = currentUserId();
        PayrollContributionPolicy policy = contributionPolicyService.getById(id);
        if (reviewerId == null) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.UNAUTHORIZED, "无法识别当前审核人");
        }
        if (policy == null) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.RESOURCE_NOT_FOUND, "五险一金政策不存在");
        }
        if (!"draft".equalsIgnoreCase(policy.getStatus())) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.INVALID_STATUS, "只有草稿政策可以提交复核");
        }
        policy.setStatus("review");
        policy.setReviewedBy(reviewerId);
        policy.setReviewedAt(java.time.LocalDateTime.now());
        policy.setUpdateBy(String.valueOf(reviewerId));
        contributionPolicyService.updateById(policy);
        return ApiResponse.success(policy);
    }

    @PostMapping("/contribution-policies/{id}/publish")
    public ApiResponse<PayrollContributionPolicy> publishContributionPolicy(@PathVariable Long id) {
        Long publisherId = currentUserId();
        PayrollContributionPolicy policy = contributionPolicyService.getById(id);
        if (publisherId == null) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.UNAUTHORIZED, "无法识别当前发布人");
        }
        if (policy == null) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.RESOURCE_NOT_FOUND, "五险一金政策不存在");
        }
        if (!"review".equalsIgnoreCase(policy.getStatus())) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.INVALID_STATUS, "政策必须先完成复核");
        }
        if (policy.getReviewedBy() == null || policy.getReviewedBy().equals(publisherId)) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.REQUEST_CONFLICT,
                    "政策复核人与发布人必须分离");
        }
        policy.setStatus("published");
        policy.setPublishedBy(publisherId);
        policy.setPublishedAt(java.time.LocalDateTime.now());
        policy.setUpdateBy(String.valueOf(publisherId));
        contributionPolicyService.updateById(policy);
        return ApiResponse.success(policy);
    }

    @GetMapping("/deduction-types")
    public ApiResponse<List<PayrollDeductionType>> deductionTypes() {
        return ApiResponse.success(Arrays.asList(PayrollDeductionType.values()));
    }

    @GetMapping("/policies")
    public ApiResponse<List<PayrollPolicyPackage>> policies(
            @RequestParam(required = false) String policyType,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(policyPackageService.list(new LambdaQueryWrapper<PayrollPolicyPackage>()
                .eq(policyType != null && !policyType.isBlank(), PayrollPolicyPackage::getPolicyType, policyType)
                .eq(regionCode != null && !regionCode.isBlank(), PayrollPolicyPackage::getRegionCode, regionCode)
                .eq(status != null && !status.isBlank(), PayrollPolicyPackage::getStatus, status)
                .orderByDesc(PayrollPolicyPackage::getEffectiveFrom)
                .orderByDesc(PayrollPolicyPackage::getVersionNo)));
    }

    @PostMapping("/policies")
    public ApiResponse<PayrollPolicyPackage> createPolicy(@Valid @RequestBody PayrollPolicyPackageRequest request) {
        PayrollPolicyPackage policy = new PayrollPolicyPackage();
        policy.setCode(request.getCode());
        policy.setName(request.getName());
        policy.setPolicyType(request.getPolicyType());
        policy.setRegionCode(request.getRegionCode());
        policy.setCollectionEntityCode(request.getCollectionEntityCode());
        policy.setPersonCategory(request.getPersonCategory());
        policy.setIndustryRiskLevel(request.getIndustryRiskLevel());
        policy.setEffectiveFrom(request.getEffectiveFrom());
        policy.setEffectiveTo(request.getEffectiveTo());
        policy.setSourceDocument(request.getSourceDocument());
        policy.setSourceUrl(request.getSourceUrl());
        policy.setPayloadJson(request.getPayloadJson());
        policy.setStatus("draft");
        policy.setVersionNo(request.getVersionNo() == null ? nextPolicyVersion(request.getCode()) : request.getVersionNo());
        policyPackageService.save(policy);
        return ApiResponse.success(policy);
    }

    @PostMapping("/policies/{id}/review")
    public ApiResponse<PayrollPolicyPackage> reviewPolicy(@PathVariable Long id) {
        Long reviewerId = currentUserId();
        if (reviewerId == null) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.UNAUTHORIZED, "无法识别当前审核人");
        }
        PayrollPolicyPackage policy = policyPackageService.getById(id);
        if (policy == null) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.RESOURCE_NOT_FOUND, "政策版本不存在");
        }
        if (!"draft".equalsIgnoreCase(policy.getStatus())) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.INVALID_STATUS, "只有草稿政策可以提交复核");
        }
        policy.setStatus("review");
        policy.setReviewedBy(reviewerId);
        policy.setReviewedAt(java.time.LocalDateTime.now());
        policyPackageService.updateById(policy);
        return ApiResponse.success(policy);
    }

    @PostMapping("/policies/{id}/publish")
    public ApiResponse<PayrollPolicyPackage> publishPolicy(@PathVariable Long id) {
        Long publisherId = currentUserId();
        if (publisherId == null) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.UNAUTHORIZED, "无法识别当前发布人");
        }
        PayrollPolicyPackage policy = policyPackageService.getById(id);
        if (policy == null) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.RESOURCE_NOT_FOUND, "政策版本不存在");
        }
        if (!"review".equalsIgnoreCase(policy.getStatus())) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.INVALID_STATUS, "政策必须先完成复核");
        }
        if (policy.getReviewedBy() == null || policy.getReviewedBy().equals(publisherId)) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.REQUEST_CONFLICT,
                    "政策复核人与发布人必须分离");
        }
        policy.setStatus("published");
        policy.setPublishedBy(publisherId);
        policy.setPublishedAt(java.time.LocalDateTime.now());
        policyPackageService.updateById(policy);
        return ApiResponse.success(policy);
    }

    @GetMapping("/deductions")
    public ApiResponse<List<PayrollTaxDeductionDeclaration>> deductions(
            @RequestParam Long employeeId,
            @RequestParam Integer taxYear) {
        return ApiResponse.success(declarationService.list(new LambdaQueryWrapper<PayrollTaxDeductionDeclaration>()
                .eq(PayrollTaxDeductionDeclaration::getEmployeeId, employeeId)
                .eq(PayrollTaxDeductionDeclaration::getTaxYear, taxYear)
                .orderByAsc(PayrollTaxDeductionDeclaration::getDeductionType)
                .orderByAsc(PayrollTaxDeductionDeclaration::getEffectiveFrom)));
    }

    @PostMapping("/deductions")
    public ApiResponse<PayrollTaxDeductionDeclaration> createDeduction(
            @Valid @RequestBody PayrollTaxDeductionDeclarationRequest request) {
        if (!PayrollDeductionType.supported(request.getDeductionType())) {
            return ApiResponse.error(com.yiyundao.compensation.common.response.ErrorCode.PARAM_INVALID,
                    "不支持的扣除类型：" + request.getDeductionType());
        }
        PayrollTaxDeductionDeclaration declaration = new PayrollTaxDeductionDeclaration();
        declaration.setEmployeeId(request.getEmployeeId());
        declaration.setTaxYear(request.getTaxYear());
        declaration.setDeductionType(request.getDeductionType());
        declaration.setSubjectKey(request.getSubjectKey());
        declaration.setAllocationRatio(request.getAllocationRatio());
        declaration.setMonthlyAmount(request.getMonthlyAmount());
        declaration.setAnnualAmount(request.getAnnualAmount());
        declaration.setEffectiveFrom(request.getEffectiveFrom());
        declaration.setEffectiveTo(request.getEffectiveTo());
        declaration.setCredentialRef(request.getCredentialRef());
        declaration.setEvidenceJson(request.getEvidenceJson());
        declaration.setSourceType(request.getSourceType());
        declaration.setStatus("pending");
        return ApiResponse.success(declarationService.saveValidated(declaration));
    }

    @GetMapping("/tax-ledger")
    public ApiResponse<List<PayrollTaxLedger>> taxLedger(
            @RequestParam Long employeeId,
            @RequestParam Integer taxYear) {
        return ApiResponse.success(taxLedgerService.list(new LambdaQueryWrapper<PayrollTaxLedger>()
                .eq(PayrollTaxLedger::getEmployeeId, employeeId)
                .eq(PayrollTaxLedger::getTaxYear, taxYear)
                .orderByAsc(PayrollTaxLedger::getTaxMonth)));
    }

    @GetMapping("/enrollments")
    public ApiResponse<List<PayrollEnrollmentResponse>> enrollments(@RequestParam Long employeeId) {
        return ApiResponse.success(enrollmentService.list(new LambdaQueryWrapper<PayrollEnrollment>()
                        .eq(PayrollEnrollment::getEmployeeId, employeeId)
                        .orderByAsc(PayrollEnrollment::getContributionType)
                        .orderByAsc(PayrollEnrollment::getEffectiveFrom))
                .stream().map(PayrollEnrollmentResponse::from).toList());
    }

    @PostMapping("/enrollments")
    public ApiResponse<PayrollEnrollmentResponse> createEnrollment(
            @Valid @RequestBody PayrollEnrollmentRequest request) {
        PayrollEnrollment enrollment = new PayrollEnrollment();
        enrollment.setEmployeeId(request.getEmployeeId());
        enrollment.setContributionType(request.getContributionType());
        enrollment.setRegionCode(request.getRegionCode());
        enrollment.setCollectionEntityCode(request.getCollectionEntityCode());
        enrollment.setAccountNoEncrypted(request.getAccountNoEncrypted());
        enrollment.setEffectiveFrom(request.getEffectiveFrom());
        enrollment.setEffectiveTo(request.getEffectiveTo());
        enrollment.setStatus(request.getStatus());
        enrollment.setPrimary(request.getPrimary());
        enrollment.setEventType(request.getEventType());
        enrollment.setPolicyId(request.getPolicyId());
        return ApiResponse.success(PayrollEnrollmentResponse.from(enrollmentService.saveValidated(enrollment)));
    }

    @GetMapping("/traces/{lineId}")
    public ApiResponse<List<PayrollCalculationTrace>> traces(@PathVariable Long lineId) {
        return ApiResponse.success(traceService.list(new LambdaQueryWrapper<PayrollCalculationTrace>()
                .eq(PayrollCalculationTrace::getPayrollLineId, lineId)
                .orderByAsc(PayrollCalculationTrace::getSequence)));
    }

    private Long nextPolicyVersion(String code) {
        PayrollPolicyPackage latest = policyPackageService.getOne(new LambdaQueryWrapper<PayrollPolicyPackage>()
                .eq(PayrollPolicyPackage::getCode, code)
                .orderByDesc(PayrollPolicyPackage::getVersionNo)
                .last("limit 1"));
        return latest == null || latest.getVersionNo() == null ? 1L : latest.getVersionNo() + 1;
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        try {
            SysUser user = sysUserService.findByUsername(authentication.getName());
            if (user != null && user.getId() != null) {
                return user.getId();
            }
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
