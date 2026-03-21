package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.PayrollApprovalProjectionMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollApprovalProjection;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.service.PayrollApprovalProjectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class PayrollApprovalProjectionServiceImpl
        extends ServiceImpl<PayrollApprovalProjectionMapper, PayrollApprovalProjection>
        implements PayrollApprovalProjectionService {

    @Override
    @Transactional
    public PayrollApprovalProjection createOrUpdatePending(PayrollBatch batch,
                                                           PayrollDistribution distribution,
                                                           Long workflowId,
                                                           Long submitterId) {
        PayrollApprovalProjection projection = getByDistributionId(distribution.getId());
        if (projection == null && workflowId != null) {
            projection = getByWorkflowId(workflowId);
        }
        if (projection == null) {
            projection = new PayrollApprovalProjection();
        }
        projection.setBatchId(batch.getId());
        projection.setBatchRevision(batch.getBatchRevision());
        projection.setDistributionId(distribution.getId());
        projection.setWorkflowId(workflowId);
        projection.setBusinessStatus("IN_PROGRESS");
        projection.setSubmitterId(submitterId);
        projection.setSubmittedAt(LocalDateTime.now());
        projection.setCompletedAt(null);
        projection.setResult(null);
        projection.setCurrentApproverId(null);
        if (projection.getId() == null) {
            save(projection);
        } else {
            updateById(projection);
        }
        return projection;
    }

    @Override
    public PayrollApprovalProjection getByDistributionId(Long distributionId) {
        return getOne(new LambdaQueryWrapper<PayrollApprovalProjection>()
                .eq(PayrollApprovalProjection::getDistributionId, distributionId)
                .last("limit 1"));
    }

    @Override
    public PayrollApprovalProjection getByWorkflowId(Long workflowId) {
        return getOne(new LambdaQueryWrapper<PayrollApprovalProjection>()
                .eq(PayrollApprovalProjection::getWorkflowId, workflowId)
                .last("limit 1"));
    }

    @Override
    @Transactional
    public void markApproved(Long workflowId, Long currentApproverId) {
        markFinal(workflowId, currentApproverId, "APPROVED", "APPROVED");
    }

    @Override
    @Transactional
    public void markRejected(Long workflowId, Long currentApproverId, String result) {
        markFinal(workflowId, currentApproverId, "REJECTED", result == null ? "REJECTED" : result);
    }

    @Override
    @Transactional
    public void markCancelled(Long workflowId, Long currentApproverId, String result) {
        markFinal(workflowId, currentApproverId, "CANCELLED", result == null ? "CANCELLED" : result);
    }

    private void markFinal(Long workflowId, Long currentApproverId, String businessStatus, String result) {
        PayrollApprovalProjection projection = getByWorkflowId(workflowId);
        if (projection == null) {
            return;
        }
        projection.setBusinessStatus(businessStatus);
        projection.setCurrentApproverId(currentApproverId);
        projection.setCompletedAt(LocalDateTime.now());
        projection.setResult(result);
        updateById(projection);
    }
}
