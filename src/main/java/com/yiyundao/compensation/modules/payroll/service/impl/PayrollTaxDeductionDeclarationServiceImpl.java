package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.PayrollTaxDeductionDeclarationMapper;
import com.yiyundao.compensation.modules.payroll.compliance.PayrollDeductionType;
import com.yiyundao.compensation.modules.payroll.entity.PayrollTaxDeductionDeclaration;
import com.yiyundao.compensation.modules.payroll.service.PayrollTaxDeductionDeclarationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class PayrollTaxDeductionDeclarationServiceImpl
        extends ServiceImpl<PayrollTaxDeductionDeclarationMapper, PayrollTaxDeductionDeclaration>
        implements PayrollTaxDeductionDeclarationService {

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
            java.math.BigDecimal annualTotal = existing.stream()
                    .filter(item -> "individual_pension".equalsIgnoreCase(item.getDeductionType()))
                    .map(this::annualizedAmount)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                    .add(annualizedAmount(declaration));
            if (annualTotal.compareTo(new java.math.BigDecimal("12000")) > 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "个人养老金年度扣除累计不得超过12000元");
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
