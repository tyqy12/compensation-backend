package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.modules.payroll.entity.PayrollContributionPolicy;

public interface PayrollContributionPolicyService extends IService<PayrollContributionPolicy> {
    PayrollContributionPolicy saveValidated(PayrollContributionPolicy policy) throws BusinessException;
}
