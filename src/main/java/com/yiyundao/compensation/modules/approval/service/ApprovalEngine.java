package com.yiyundao.compensation.modules.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.modules.approval.entity.ApprovalStep;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.service.OrganizationSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEngine extends ServiceImpl<ApprovalWorkflowMapper, ApprovalWorkflow> {

    private final com.yiyundao.compensation.modules.approval.service.ApprovalStepService approvalStepService;
    private final EmployeeService employeeService;
    private final SysUserService sysUserService;
    private final OrganizationSyncService organizationSyncService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Long startWorkflow(WorkflowType workflowType, String businessKey, String businessType,
                            Long initiatorId, Map<String, Object> workflowData) {
        try {
            ApprovalWorkflow workflow = new ApprovalWorkflow();
            workflow.setWorkflowName(generateWorkflowName(workflowType, businessKey));
            workflow.setWorkflowType(workflowType);
            workflow.setBusinessKey(businessKey);
            workflow.setBusinessType(businessType);
            workflow.setCurrentStep(1);
            workflow.setStatus(ApprovalStatus.PENDING);
            workflow.setInitiatorId(initiatorId);
            workflow.setSubmitTime(LocalDateTime.now());

            if (workflowData != null && !workflowData.isEmpty()) {
                workflow.setWorkflowData(objectMapper.writeValueAsString(workflowData));
            }

            save(workflow);

            List<ApprovalStep> steps = generateApprovalSteps(workflow, workflowType, workflowData);
            workflow.setTotalSteps(steps.size());

            if (!steps.isEmpty()) {
                workflow.setCurrentApproverId(steps.get(0).getApproverId());
                for (ApprovalStep step : steps) {
                    step.setWorkflowId(workflow.getId());
                    approvalStepService.save(step);
                }
                sendApprovalNotification(steps.get(0));
            }

            updateById(workflow);
            return workflow.getId();

        } catch (Exception e) {
            throw new RuntimeException("启动审批流程失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void processApproval(Long workflowId, Long approverId, ApprovalStatus decision,
                              String comment) {
        ApprovalWorkflow workflow = getById(workflowId);
        if (workflow == null) throw new IllegalArgumentException("审批流程不存在: " + workflowId);
        if (workflow.getStatus() != ApprovalStatus.PENDING) throw new IllegalStateException("流程不是待审批状态: " + workflow.getStatus());
        if (!workflow.getCurrentApproverId().equals(approverId)) throw new IllegalArgumentException("无权限审批该流程");

        ApprovalStep currentStep = approvalStepService.getCurrentStep(workflowId, workflow.getCurrentStep());
        if (currentStep == null) throw new IllegalStateException("找不到当前审批步骤");

        currentStep.setStatus(decision);
        currentStep.setApproveTime(LocalDateTime.now());
        currentStep.setApproveComment(comment);
        if (decision == ApprovalStatus.REJECTED) currentStep.setRejectReason(comment);
        approvalStepService.updateById(currentStep);

        if (decision == ApprovalStatus.APPROVED) {
            if (workflow.getCurrentStep() < workflow.getTotalSteps()) {
                moveToNextStep(workflow);
            } else {
                completeWorkflow(workflow, ApprovalStatus.APPROVED);
            }
        } else {
            completeWorkflow(workflow, ApprovalStatus.REJECTED);
        }
    }

    @Transactional
    public void cancelWorkflow(Long workflowId, Long operatorId, String reason) {
        ApprovalWorkflow workflow = getById(workflowId);
        if (workflow == null) throw new IllegalArgumentException("审批流程不存在: " + workflowId);
        if (!workflow.getInitiatorId().equals(operatorId)) throw new IllegalArgumentException("只有发起人可以撤销流程");
        if (workflow.getStatus() != ApprovalStatus.PENDING) throw new IllegalStateException("只能撤销待审批的流程");
        completeWorkflow(workflow, ApprovalStatus.CANCELLED);
    }

    public List<ApprovalWorkflow> getPendingWorkflows(Long approverId) {
        LambdaQueryWrapper<ApprovalWorkflow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApprovalWorkflow::getCurrentApproverId, approverId)
                   .eq(ApprovalWorkflow::getStatus, ApprovalStatus.PENDING)
                   .orderByAsc(ApprovalWorkflow::getSubmitTime);
        return list(queryWrapper);
    }

    public List<ApprovalWorkflow> getMyWorkflows(Long initiatorId) {
        LambdaQueryWrapper<ApprovalWorkflow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApprovalWorkflow::getInitiatorId, initiatorId)
                   .orderByDesc(ApprovalWorkflow::getSubmitTime);
        return list(queryWrapper);
    }

    private void moveToNextStep(ApprovalWorkflow workflow) {
        int nextStepNo = workflow.getCurrentStep() + 1;
        ApprovalStep nextStep = approvalStepService.getStepByNo(workflow.getId(), nextStepNo);
        if (nextStep != null) {
            workflow.setCurrentStep(nextStepNo);
            workflow.setCurrentApproverId(nextStep.getApproverId());
            updateById(workflow);
            sendApprovalNotification(nextStep);
        }
    }

    private void completeWorkflow(ApprovalWorkflow workflow, ApprovalStatus finalStatus) {
        workflow.setStatus(finalStatus);
        workflow.setCompleteTime(LocalDateTime.now());
        workflow.setCurrentApproverId(null);
        updateById(workflow);
        sendWorkflowCompleteNotification(workflow, finalStatus);
    }

    private List<ApprovalStep> generateApprovalSteps(ApprovalWorkflow workflow, WorkflowType workflowType,
                                                   Map<String, Object> workflowData) {
        List<ApprovalStep> steps = List.of();
        switch (workflowType) {
            case BATCH -> steps = generateBatchPaymentSteps(workflow);
            case ADHOC -> steps = generateAdhocPaymentSteps(workflow);
            case OFFLINE -> steps = generateOfflineEmployeeSteps(workflow);
        }
        return steps;
    }

    private List<ApprovalStep> generateBatchPaymentSteps(ApprovalWorkflow workflow) {
        ApprovalStep step1 = createApprovalStep(1, "部门负责人审批", 2L, "张部长", 24);
        ApprovalStep step2 = createApprovalStep(2, "财务负责人审批", 3L, "李财务", 24);
        ApprovalStep step3 = createApprovalStep(3, "总经理审批", 1L, "王总", 48);
        return List.of(step1, step2, step3);
    }

    private List<ApprovalStep> generateAdhocPaymentSteps(ApprovalWorkflow workflow) {
        ApprovalStep step1 = createApprovalStep(1, "直接上级审批", 2L, "张主管", 24);
        ApprovalStep step2 = createApprovalStep(2, "财务审批", 3L, "李财务", 24);
        return List.of(step1, step2);
    }

    private List<ApprovalStep> generateOfflineEmployeeSteps(ApprovalWorkflow workflow) {
        ApprovalStep step1 = createApprovalStep(1, "管理员审批", 1L, "系统管理员", 24);
        return List.of(step1);
    }

    private ApprovalStep createApprovalStep(int stepNo, String stepName, Long approverId,
                                          String approverName, int timeoutHours) {
        ApprovalStep step = new ApprovalStep();
        step.setStepNo(stepNo);
        step.setStepName(stepName);
        step.setApproverId(approverId);
        step.setApproverName(approverName);
        step.setStatus(ApprovalStatus.PENDING);
        step.setTimeoutHours(timeoutHours);
        return step;
    }

    private void sendApprovalNotification(ApprovalStep step) {
        try {
            SysUser approver = sysUserService.getById(step.getApproverId());
            if (approver == null || approver.getPlatformUserId() == null) return;
            String message = String.format("您有新的审批任务：%s，请及时处理。", step.getStepName());
            organizationSyncService.sendNotification(
                approver.getPlatformType(),
                approver.getPlatformUserId(),
                message
            );
        } catch (Exception ignored) { }
    }

    private void sendWorkflowCompleteNotification(ApprovalWorkflow workflow, ApprovalStatus finalStatus) {
        try {
            SysUser initiator = sysUserService.getById(workflow.getInitiatorId());
            if (initiator == null || initiator.getPlatformUserId() == null) return;
            String statusText = finalStatus == ApprovalStatus.APPROVED ? "审批通过" :
                              finalStatus == ApprovalStatus.REJECTED ? "审批拒绝" : "已取消";
            String message = String.format("您的审批流程 %s 已%s。", workflow.getWorkflowName(), statusText);
            organizationSyncService.sendNotification(
                initiator.getPlatformType(),
                initiator.getPlatformUserId(),
                message
            );
        } catch (Exception ignored) { }
    }

    private String generateWorkflowName(WorkflowType workflowType, String businessKey) {
        String prefix = switch (workflowType) {
            case BATCH -> "批量支付审批";
            case ADHOC -> "临时支付审批";
            case OFFLINE -> "离线员工审批";
        };
        return prefix + "-" + businessKey;
    }

    public Map<String, Object> getWorkflowData(Long workflowId) {
        ApprovalWorkflow workflow = getById(workflowId);
        if (workflow != null && workflow.getWorkflowData() != null) {
            try {
                return objectMapper.readValue(workflow.getWorkflowData(), Map.class);
            } catch (JsonProcessingException e) {
                // ignore
            }
        }
        return Map.of();
    }
}
