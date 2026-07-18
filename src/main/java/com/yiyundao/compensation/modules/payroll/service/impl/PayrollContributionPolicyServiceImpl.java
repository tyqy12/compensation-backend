package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.PayrollContributionPolicyMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollContributionPolicy;
import com.yiyundao.compensation.modules.payroll.service.PayrollContributionPolicyService;
import org.springframework.stereotype.Service;

@Service
public class PayrollContributionPolicyServiceImpl
        extends ServiceImpl<PayrollContributionPolicyMapper, PayrollContributionPolicy>
        implements PayrollContributionPolicyService {

    @Override
    public PayrollContributionPolicy saveValidated(PayrollContributionPolicy policy) {
        if (policy == null || policy.getCode() == null || policy.getCode().isBlank()
                || policy.getEffectiveFrom() == null
                || policy.getRegionCode() == null || policy.getRegionCode().isBlank()
                || policy.getContributionType() == null || policy.getContributionType().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "地区、险种和生效日期不能为空");
        }
        if (policy.getEffectiveTo() != null && policy.getEffectiveTo().isBefore(policy.getEffectiveFrom())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "政策失效日期不能早于生效日期");
        }
        if (policy.getBaseMin() != null && policy.getBaseMax() != null
                && policy.getBaseMax().compareTo(policy.getBaseMin()) < 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "缴费基数上限不能低于下限");
        }
        if (policy.getRoundingMode() == null || policy.getRoundingMode().isBlank()) {
            policy.setRoundingMode("HALF_UP");
        }
        if (policy.getStatus() == null || policy.getStatus().isBlank()) {
            policy.setStatus("draft");
        }
        if (policy.getVersionNo() == null) {
            PayrollContributionPolicy latest = getOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayrollContributionPolicy>()
                    .eq(PayrollContributionPolicy::getCode, policy.getCode())
                    .orderByDesc(PayrollContributionPolicy::getVersionNo)
                    .last("limit 1"));
            policy.setVersionNo(latest == null || latest.getVersionNo() == null ? 1L : latest.getVersionNo() + 1);
        }
        saveOrUpdate(policy);
        return policy;
    }
}
