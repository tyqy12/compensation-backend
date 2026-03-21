package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollApprovalProjection;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;

public interface PayrollApprovalProjectionService extends IService<PayrollApprovalProjection> {

    PayrollApprovalProjection createOrUpdatePending(PayrollBatch batch,
                                                    PayrollDistribution distribution,
                                                    Long workflowId,
                                                    Long submitterId);

    PayrollApprovalProjection getByDistributionId(Long distributionId);

    PayrollApprovalProjection getByWorkflowId(Long workflowId);

    void markApproved(Long workflowId, Long currentApproverId);

    void markRejected(Long workflowId, Long currentApproverId, String result);

    void markCancelled(Long workflowId, Long currentApproverId, String result);
}
