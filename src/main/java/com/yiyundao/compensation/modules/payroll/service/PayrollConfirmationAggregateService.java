package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollConfirmation;

public interface PayrollConfirmationAggregateService extends IService<PayrollConfirmation> {

    PayrollConfirmation createOrRefreshForBatch(PayrollBatch batch);

    PayrollConfirmation getByBatchIdAndRevision(Long batchId, Integer batchRevision);

    void skipConfirmation(Long confirmationId);

    void syncFromLegacyBatch(Long batchId, Integer batchRevision);

    boolean isCompletedForApproval(Long batchId, Integer batchRevision);

    void supersedeObsolete(Long batchId, Integer activeRevision);
}
