package com.yiyundao.compensation.modules.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.modules.approval.entity.ApprovalStep;
import com.yiyundao.compensation.infrastructure.dao.ApprovalStepMapper;
import com.yiyundao.compensation.modules.approval.service.ApprovalStepService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ApprovalStepServiceImpl extends ServiceImpl<ApprovalStepMapper, ApprovalStep>
        implements ApprovalStepService {

    @Override
    public ApprovalStep getCurrentStep(Long workflowId, Integer stepNo) {
        LambdaQueryWrapper<ApprovalStep> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApprovalStep::getWorkflowId, workflowId)
                   .eq(ApprovalStep::getStepNo, stepNo);
        return getOne(queryWrapper);
    }

    @Override
    public ApprovalStep getStepByNo(Long workflowId, Integer stepNo) {
        LambdaQueryWrapper<ApprovalStep> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApprovalStep::getWorkflowId, workflowId)
                   .eq(ApprovalStep::getStepNo, stepNo);
        return getOne(queryWrapper);
    }
}
