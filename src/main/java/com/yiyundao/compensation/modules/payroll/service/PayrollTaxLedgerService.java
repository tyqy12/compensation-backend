package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollTaxLedger;

public interface PayrollTaxLedgerService extends IService<PayrollTaxLedger> {
    PayrollTaxLedger findLatestBefore(Long employeeId, Long withholdingEntityId, int taxYear, int taxMonth);

    int postBatch(Long payrollBatchId, Integer payrollBatchRevision);

    int reverseBatch(Long payrollBatchId, Integer payrollBatchRevision);
}
