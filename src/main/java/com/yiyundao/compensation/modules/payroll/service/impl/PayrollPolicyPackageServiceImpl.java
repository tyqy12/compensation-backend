package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.PayrollPolicyPackageMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollPolicyPackage;
import com.yiyundao.compensation.modules.payroll.service.PayrollPolicyPackageService;
import org.springframework.stereotype.Service;

@Service
public class PayrollPolicyPackageServiceImpl extends ServiceImpl<PayrollPolicyPackageMapper, PayrollPolicyPackage>
        implements PayrollPolicyPackageService {
}
