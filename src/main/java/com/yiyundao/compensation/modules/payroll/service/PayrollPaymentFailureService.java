package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollPaymentFailure;

import java.util.List;

public interface PayrollPaymentFailureService extends IService<PayrollPaymentFailure> {

    PayrollPaymentFailure recordFailure(Long workflowId, Long payrollBatchId, String businessKey, String errorMessage);

    void markResolved(Long workflowId, String paymentBatchNo);

    void markResolvedByPayrollBatchId(Long payrollBatchId, String paymentBatchNo);

    PayrollPaymentFailure retry(Long failureId, boolean triggerTransfer);

    List<PayrollPaymentFailure> listUnresolved(int limit);
}
