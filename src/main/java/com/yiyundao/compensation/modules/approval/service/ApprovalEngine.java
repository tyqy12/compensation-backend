package com.yiyundao.compensation.modules.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEngine extends ServiceImpl<ApprovalWorkflowMapper, ApprovalWorkflow> {

    private final com.yiyundao.compensation.modules.approval.service.ApprovalStepService approvalStepService;
    private final EmployeeService employeeService;
    private final SysUserService sysUserService;
    private final OrganizationSyncService organizationSyncService;
    private final ObjectMapper objectMapper;
    private final com.yiyundao.compensation.modules.system.service.SysConfigService sysConfigService;
    private final com.yiyundao.compensation.modules.user.service.PlatformLinkApprovalHandler platformLinkApprovalHandler;
    private final com.yiyundao.compensation.modules.rbac.service.ResourceApprovalHandler resourceApprovalHandler;

    private static final String PAYROLL_APPROVAL_FLOW_KEY = "payroll.approval.flow";

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

            // Generate steps first so we can persist workflow with non-null total_steps
            List<ApprovalStep> steps = generateApprovalSteps(workflow, workflowType, workflowData);
            workflow.setTotalSteps(steps.size());
            if (!steps.isEmpty()) {
                workflow.setCurrentApproverId(steps.get(0).getApproverId());
            }

            // Insert workflow now that all non-nullable fields are set
            save(workflow);

            if (!steps.isEmpty()) {
                for (ApprovalStep step : steps) {
                    step.setWorkflowId(workflow.getId());
                    approvalStepService.save(step);
                }
                sendApprovalNotification(steps.get(0));
            }

            // No further updates required here since we inserted with final values
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
        // 业务回调（平台绑定等）
        try {
            platformLinkApprovalHandler.handle(workflow, finalStatus);
        } catch (Exception ignored) {}
        try {
            resourceApprovalHandler.handle(workflow, finalStatus);
        } catch (Exception ignored) {}
    }

    private List<ApprovalStep> generateApprovalSteps(ApprovalWorkflow workflow, WorkflowType workflowType,
                                                   Map<String, Object> workflowData) {
        return switch (workflowType) {
            case BATCH -> generateBatchPaymentSteps();
            case ADHOC -> generateAdhocPaymentSteps();
            case OFFLINE -> generateOfflineEmployeeSteps();
        };
    }

    private List<ApprovalStep> generateBatchPaymentSteps() {
        List<ApprovalConfigStep> configured = loadPayrollApprovalConfig();
        if (configured.isEmpty()) {
            configured = defaultPayrollSteps();
        }
        List<ApprovalStep> steps = buildStepsFromConfig(configured, defaultTimeoutHours());
        if (steps.isEmpty()) {
            steps = buildStepsFromConfig(defaultPayrollSteps(), defaultTimeoutHours());
        }
        return steps;
    }

    private List<ApprovalStep> generateAdhocPaymentSteps() {
        List<ApprovalConfigStep> config = new ArrayList<>();
        config.add(ApprovalConfigStep.of(1, "直接上级审批", "ROLE_MANAGER", null, null, 24, false));
        config.add(ApprovalConfigStep.of(2, "财务审批", "ROLE_FINANCE", null, null, 24, false));
        return buildStepsFromConfig(config, defaultTimeoutHours());
    }

    private List<ApprovalStep> generateOfflineEmployeeSteps() {
        List<ApprovalConfigStep> config = List.of(ApprovalConfigStep.of(1, "管理员审批", "ROLE_ADMIN", null, null, 24, false));
        return buildStepsFromConfig(config, defaultTimeoutHours());
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

    private int defaultTimeoutHours() {
        return Optional.ofNullable(sysConfigService.getInt("approval.timeout.hours", 24)).orElse(24);
    }

    private List<ApprovalConfigStep> loadPayrollApprovalConfig() {
        String raw = sysConfigService.getString(PAYROLL_APPROVAL_FLOW_KEY, null);
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<ApprovalConfigStep>>() {});
        } catch (Exception e) {
            log.warn("解析审批链配置失败({}): {}", PAYROLL_APPROVAL_FLOW_KEY, e.getMessage());
            return List.of();
        }
    }

    private List<ApprovalConfigStep> defaultPayrollSteps() {
        List<ApprovalConfigStep> defaults = new ArrayList<>();
        defaults.add(ApprovalConfigStep.of(1, "部门负责人审批", "ROLE_MANAGER", null, null, 24, false));
        defaults.add(ApprovalConfigStep.of(2, "财务负责人审批", "ROLE_FINANCE", null, null, 24, false));
        defaults.add(ApprovalConfigStep.of(3, "总监审批", "ROLE_ADMIN", null, null, 48, true));
        return defaults;
    }

    private List<ApprovalStep> buildStepsFromConfig(List<ApprovalConfigStep> configSteps, int defaultTimeout) {
        if (configSteps == null || configSteps.isEmpty()) {
            return List.of();
        }
        List<ApprovalStep> steps = new ArrayList<>();
        int lastAutoStep = 0;
        for (ApprovalConfigStep cfg : configSteps.stream()
                .sorted(Comparator.comparing(step -> Optional.ofNullable(step.stepNo).orElse(Integer.MAX_VALUE)))
                .toList()) {
            int stepNo = cfg.stepNo != null ? cfg.stepNo : lastAutoStep + 1;
            lastAutoStep = stepNo;

            SysUser approver = resolveApprover(cfg);
            if (approver == null) {
                if (Boolean.TRUE.equals(cfg.optional)) {
                    log.info("审批步骤{}({}) 未找到审批人，已跳过(可选)", stepNo, cfg.stepName);
                    continue;
                }
                approver = fallbackAdmin();
                if (approver == null) {
                    log.warn("审批步骤{}({}) 未找到审批人且无管理员兜底，跳过该步骤", stepNo, cfg.stepName);
                    continue;
                }
            }

            String approverName = Optional.ofNullable(approver.getRealName()).filter(StringUtils::hasText)
                    .orElse(approver.getUsername());
            String stepName = StringUtils.hasText(cfg.stepName)
                    ? cfg.stepName
                    : (StringUtils.hasText(cfg.role) ? cfg.role : "审批步骤" + stepNo);
            int timeout = cfg.timeoutHours != null ? cfg.timeoutHours : defaultTimeout;

            steps.add(createApprovalStep(stepNo, stepName, approver.getId(), approverName, timeout));
        }
        return steps;
    }

    private SysUser resolveApprover(ApprovalConfigStep cfg) {
        if (cfg == null) {
            return null;
        }
        if (cfg.approverId != null) {
            return sysUserService.getById(cfg.approverId);
        }
        if (StringUtils.hasText(cfg.approverUsername)) {
            return sysUserService.findByUsername(cfg.approverUsername.trim());
        }
        if (StringUtils.hasText(cfg.role)) {
            return sysUserService.findFirstByRole(cfg.role.trim());
        }
        return null;
    }

    private SysUser fallbackAdmin() {
        SysUser admin = sysUserService.findFirstByRole("ROLE_ADMIN");
        if (admin != null) {
            return admin;
        }
        return sysUserService.findByUsername("admin");
    }

    private static class ApprovalConfigStep {
        public Integer stepNo;
        public String stepName;
        public String role;
        public Long approverId;
        public String approverUsername;
        public Integer timeoutHours;
        public Boolean optional;

        static ApprovalConfigStep of(Integer stepNo, String stepName, String role,
                                     Long approverId, String approverUsername,
                                     Integer timeoutHours, Boolean optional) {
            ApprovalConfigStep step = new ApprovalConfigStep();
            step.stepNo = stepNo;
            step.stepName = stepName;
            step.role = role;
            step.approverId = approverId;
            step.approverUsername = approverUsername;
            step.timeoutHours = timeoutHours;
            step.optional = optional;
            return step;
        }
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
