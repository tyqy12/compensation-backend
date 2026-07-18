package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.PayrollEnrollmentMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollEnrollment;
import com.yiyundao.compensation.modules.payroll.service.PayrollEnrollmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PayrollEnrollmentServiceImpl extends ServiceImpl<PayrollEnrollmentMapper, PayrollEnrollment>
        implements PayrollEnrollmentService {

    @Override
    @Transactional
    public PayrollEnrollment saveValidated(PayrollEnrollment enrollment) {
        if (enrollment == null || enrollment.getEmployeeId() == null
                || enrollment.getContributionType() == null
                || enrollment.getEffectiveFrom() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "员工、险种和参保起始日期不能为空");
        }
        if (enrollment.getEffectiveTo() != null
                && enrollment.getEffectiveTo().isBefore(enrollment.getEffectiveFrom())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "参保结束日期不能早于起始日期");
        }
        LocalDate from = enrollment.getEffectiveFrom();
        LocalDate to = enrollment.getEffectiveTo();
        List<PayrollEnrollment> active = list(new LambdaQueryWrapper<PayrollEnrollment>()
                .eq(PayrollEnrollment::getEmployeeId, enrollment.getEmployeeId())
                .eq(PayrollEnrollment::getContributionType, enrollment.getContributionType())
                .eq(PayrollEnrollment::getStatus, "active")
                .ne(enrollment.getId() != null, PayrollEnrollment::getId, enrollment.getId()));
        boolean overlap = active.stream().anyMatch(existing -> overlaps(
                from, to, existing.getEffectiveFrom(), existing.getEffectiveTo()));
        if (overlap) {
            throw new BusinessException(ErrorCode.REQUEST_CONFLICT,
                    "同一员工同一险种的参保关系在有效期内重叠，请先办理转出、封存或清退");
        }
        if (enrollment.getStatus() == null || enrollment.getStatus().isBlank()) {
            enrollment.setStatus("active");
        }
        if (enrollment.getPrimary() == null) {
            enrollment.setPrimary(Boolean.TRUE);
        }
        saveOrUpdate(enrollment);
        return enrollment;
    }

    private boolean overlaps(LocalDate leftFrom, LocalDate leftTo, LocalDate rightFrom, LocalDate rightTo) {
        if (rightFrom == null) {
            return false;
        }
        return !endBefore(leftTo, rightFrom) && !endBefore(rightTo, leftFrom);
    }

    private boolean endBefore(LocalDate end, LocalDate start) {
        return end != null && start != null && end.isBefore(start);
    }
}
