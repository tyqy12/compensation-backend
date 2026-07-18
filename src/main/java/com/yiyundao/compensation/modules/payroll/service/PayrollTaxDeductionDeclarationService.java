package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollTaxDeductionDeclaration;

public interface PayrollTaxDeductionDeclarationService extends IService<PayrollTaxDeductionDeclaration> {
    PayrollTaxDeductionDeclaration saveValidated(PayrollTaxDeductionDeclaration declaration);

    PayrollTaxDeductionDeclaration approveValidated(Long declarationId);
}
