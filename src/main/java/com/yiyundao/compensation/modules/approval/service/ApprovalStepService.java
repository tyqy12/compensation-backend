package com.yiyundao.compensation.modules.approval.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.approval.entity.ApprovalStep;

public interface ApprovalStepService extends IService<ApprovalStep> {
    ApprovalStep getCurrentStep(Long workflowId, Integer stepNo);
    ApprovalStep getStepByNo(Long workflowId, Integer stepNo);
    java.util.List<ApprovalStep> listByWorkflow(Long workflowId);
}
