package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.PayrollTaxLedgerMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollTaxLedger;
import com.yiyundao.compensation.modules.payroll.service.PayrollTaxLedgerService;
import org.springframework.stereotype.Service;

@Service
public class PayrollTaxLedgerServiceImpl extends ServiceImpl<PayrollTaxLedgerMapper, PayrollTaxLedger>
        implements PayrollTaxLedgerService {

    @Override
    public PayrollTaxLedger findLatestBefore(Long employeeId, Long withholdingEntityId, int taxYear, int taxMonth) {
        LambdaQueryWrapper<PayrollTaxLedger> query = new LambdaQueryWrapper<PayrollTaxLedger>()
                .eq(PayrollTaxLedger::getEmployeeId, employeeId)
                .eq(PayrollTaxLedger::getTaxYear, taxYear)
                .lt(PayrollTaxLedger::getTaxMonth, taxMonth)
                .eq(PayrollTaxLedger::getStatus, "posted")
                .orderByDesc(PayrollTaxLedger::getTaxMonth)
                .orderByDesc(PayrollTaxLedger::getId)
                .last("limit 1");
        if (withholdingEntityId == null) {
            query.isNull(PayrollTaxLedger::getWithholdingEntityId);
        } else {
            query.eq(PayrollTaxLedger::getWithholdingEntityId, withholdingEntityId);
        }
        return getOne(query);
    }

    @Override
    public int postBatch(Long payrollBatchId, Integer payrollBatchRevision) {
        if (payrollBatchId == null || payrollBatchRevision == null) {
            return 0;
        }
        return baseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PayrollTaxLedger>()
                .eq(PayrollTaxLedger::getPayrollBatchId, payrollBatchId)
                .eq(PayrollTaxLedger::getPayrollBatchRevision, payrollBatchRevision)
                .eq(PayrollTaxLedger::getStatus, "draft")
                .set(PayrollTaxLedger::getStatus, "posted"));
    }

    @Override
    public int reverseBatch(Long payrollBatchId, Integer payrollBatchRevision) {
        if (payrollBatchId == null || payrollBatchRevision == null) {
            return 0;
        }
        return baseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PayrollTaxLedger>()
                .eq(PayrollTaxLedger::getPayrollBatchId, payrollBatchId)
                .eq(PayrollTaxLedger::getPayrollBatchRevision, payrollBatchRevision)
                .eq(PayrollTaxLedger::getStatus, "posted")
                .set(PayrollTaxLedger::getStatus, "reversed"));
    }
}
