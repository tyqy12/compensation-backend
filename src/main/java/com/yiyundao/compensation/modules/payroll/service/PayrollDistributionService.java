package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistributionItem;

import java.util.List;

public interface PayrollDistributionService extends IService<PayrollDistribution> {

    PayrollDistribution createOrReuseForBatch(PayrollBatch batch);

    PayrollDistribution getByBatchIdAndRevision(Long batchId, Integer batchRevision);

    List<PayrollDistributionItem> createOrRefreshItems(PayrollDistribution distribution);

    List<PayrollDistributionItem> listActiveItems(Long distributionId);

    List<PayrollDistributionItem> listRetryableItems(Long distributionId);

    void bindApprovalWorkflow(Long distributionId, Long workflowId);

    void syncFromPaymentBatch(PaymentBatch paymentBatch);

    void supersedeObsolete(Long batchId, Integer activeRevision);
}
