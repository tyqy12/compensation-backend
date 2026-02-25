package com.yiyundao.compensation.modules.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.modules.approval.config.ApprovalFlowConfigManager;
import com.yiyundao.compensation.modules.approval.entity.ApprovalStep;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityConstants;
import com.yiyundao.compensation.service.OrganizationSyncService;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 审批引擎
 * <p>
 * 负责管理审批流程的启动、流转和完成
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEngine extends ServiceImpl<ApprovalWorkflowMapper, ApprovalWorkflow> {

    private final ApprovalStepService approvalStepService;
    private final SysUserService sysUserService;
    private final OrganizationSyncService organizationSyncService;
    private final ObjectMapper objectMapper;
    private final SysConfigService sysConfigService;
    private final ApprovalFlowConfigManager approvalFlowConfigManager;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== 流程启动 ====================

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

            return workflow.getId();

        } catch (Exception e) {
            throw new RuntimeException("启动审批流程失败: " + e.getMessage(), e);
        }
    }

    // ==================== 流程处理 ====================

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

    // ==================== 流程查询 ====================

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

    // ==================== 审批步骤生成 ====================

    /**
     * 根据流程类型生成审批步骤
     */
    private List<ApprovalStep> generateApprovalSteps(ApprovalWorkflow workflow, WorkflowType workflowType,
                                                      Map<String, Object> workflowData) {
        return switch (workflowType) {
            case BATCH -> generateBatchPaymentSteps();
            case ADHOC -> generateAdhocPaymentSteps();
            case OFFLINE -> generateOfflineEmployeeSteps();
            case PERMISSION -> generatePermissionSteps();
        };
    }

    /**
     * 生成批量支付审批步骤
     */
    private List<ApprovalStep> generateBatchPaymentSteps() {
        List<ApprovalFlowConfigManager.ApprovalStepConfig> configured = loadPayrollApprovalConfig();
        if (configured.isEmpty()) {
            configured = approvalFlowConfigManager.getDefaultSteps(WorkflowType.BATCH);
        }
        return buildStepsFromConfig(configured);
    }

    /**
     * 生成临时支付审批步骤
     */
    private List<ApprovalStep> generateAdhocPaymentSteps() {
        List<ApprovalFlowConfigManager.ApprovalStepConfig> config = loadAdhocApprovalConfig();
        if (config.isEmpty()) {
            config = approvalFlowConfigManager.getDefaultSteps(WorkflowType.ADHOC);
        }
        return buildStepsFromConfig(config);
    }

    /**
     * 生成离线员工审批步骤
     */
    private List<ApprovalStep> generateOfflineEmployeeSteps() {
        List<ApprovalFlowConfigManager.ApprovalStepConfig> config = loadOfflineApprovalConfig();
        if (config.isEmpty()) {
            config = approvalFlowConfigManager.getDefaultSteps(WorkflowType.OFFLINE);
        }
        return buildStepsFromConfig(config);
    }

    /**
     * 生成权限授权审批步骤
     */
    private List<ApprovalStep> generatePermissionSteps() {
        List<ApprovalFlowConfigManager.ApprovalStepConfig> config = loadPermissionApprovalConfig();
        if (config.isEmpty()) {
            config = approvalFlowConfigManager.getDefaultSteps(WorkflowType.PERMISSION);
        }
        return buildStepsFromConfig(config);
    }

    /**
     * 从配置构建审批步骤
     */
    private List<ApprovalStep> buildStepsFromConfig(List<ApprovalFlowConfigManager.ApprovalStepConfig> configSteps) {
        if (configSteps == null || configSteps.isEmpty()) {
            return List.of();
        }

        int defaultTimeout = defaultTimeoutHours();
        List<ApprovalStep> steps = new ArrayList<>();
        int lastAutoStep = 0;

        for (ApprovalFlowConfigManager.ApprovalStepConfig cfg : configSteps.stream()
                .sorted(Comparator.comparing(step -> Optional.ofNullable(step.getStepNo()).orElse(Integer.MAX_VALUE)))
                .toList()) {
            int stepNo = cfg.getStepNo() != null ? cfg.getStepNo() : lastAutoStep + 1;
            lastAutoStep = stepNo;

            SysUser approver = resolveApprover(cfg);
            if (approver == null) {
                if (Boolean.TRUE.equals(cfg.getOptional())) {
                    log.info("审批步骤{}({}) 未找到审批人，已跳过(可选)", stepNo, cfg.getStepName());
                    continue;
                }
                approver = findFallbackAdmin();
                if (approver == null) {
                    log.warn("审批步骤{}({}) 未找到审批人且无管理员兜底，跳过该步骤", stepNo, cfg.getStepName());
                    continue;
                }
            }

            String approverName = Optional.ofNullable(approver.getRealName()).filter(StringUtils::hasText)
                    .orElse(approver.getUsername());
            String stepName = StringUtils.hasText(cfg.getStepName())
                    ? cfg.getStepName()
                    : (StringUtils.hasText(cfg.getRole()) ? cfg.getRole() : "审批步骤" + stepNo);
            int timeout = cfg.getTimeoutHours() != null ? cfg.getTimeoutHours() : defaultTimeout;

            steps.add(createApprovalStep(stepNo, stepName, approver.getId(), approverName, timeout));
        }
        return steps;
    }

    /**
     * 创建审批步骤实体
     */
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

    /**
     * 解析审批人
     */
    private SysUser resolveApprover(ApprovalFlowConfigManager.ApprovalStepConfig cfg) {
        if (cfg == null) {
            return null;
        }
        if (cfg.getApproverId() != null) {
            return sysUserService.getById(cfg.getApproverId());
        }
        if (StringUtils.hasText(cfg.getApproverUsername())) {
            return sysUserService.findByUsername(cfg.getApproverUsername().trim());
        }
        if (StringUtils.hasText(cfg.getRole())) {
            return sysUserService.findFirstByRole(cfg.getRole().trim());
        }
        return null;
    }

    // ==================== 配置加载 ====================

    /**
     * 从数据库加载批量支付审批配置
     */
    private List<ApprovalFlowConfigManager.ApprovalStepConfig> loadPayrollApprovalConfig() {
        return loadApprovalConfig(SecurityConstants.CONFIG_PAYROLL_APPROVAL_FLOW);
    }

    /**
     * 从数据库加载临时支付审批配置
     */
    private List<ApprovalFlowConfigManager.ApprovalStepConfig> loadAdhocApprovalConfig() {
        return loadApprovalConfig(SecurityConstants.CONFIG_ADHOC_APPROVAL_FLOW);
    }

    /**
     * 从数据库加载离线员工审批配置
     */
    private List<ApprovalFlowConfigManager.ApprovalStepConfig> loadOfflineApprovalConfig() {
        return loadApprovalConfig(SecurityConstants.CONFIG_OFFLINE_APPROVAL_FLOW);
    }

    /**
     * 从数据库加载权限授权审批配置
     */
    private List<ApprovalFlowConfigManager.ApprovalStepConfig> loadPermissionApprovalConfig() {
        return loadApprovalConfig(SecurityConstants.CONFIG_PERMISSION_APPROVAL_FLOW);
    }

    /**
     * 从数据库加载审批配置
     */
    private List<ApprovalFlowConfigManager.ApprovalStepConfig> loadApprovalConfig(String configKey) {
        String raw = sysConfigService.getString(configKey, null);
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<ApprovalFlowConfigManager.ApprovalStepConfig>>() {});
        } catch (Exception e) {
            log.warn("解析审批链配置失败({}): {}", configKey, e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取默认超时时间
     */
    private int defaultTimeoutHours() {
        return Optional.ofNullable(sysConfigService.getInt("approval.timeout.hours",
                SecurityConstants.DEFAULT_APPROVAL_TIMEOUT_HOURS)).orElse(SecurityConstants.DEFAULT_APPROVAL_TIMEOUT_HOURS);
    }

    // ==================== 审批人查找 ====================

    /**
     * 查找兜底管理员
     */
    private SysUser findFallbackAdmin() {
        SysUser admin = sysUserService.findFirstByRole(SecurityConstants.ROLE_ADMIN);
        if (admin != null) {
            return admin;
        }
        return sysUserService.findByUsername("admin");
    }

    // ==================== 流程流转 ====================

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

    /**
     * 完成审批流程
     * <p>
     * 使用事件驱动模式，发布 ApprovalCompletedEvent 事件，
     * 由各业务模块的 EventListener 监听并处理后续业务逻辑。
     * </p>
     *
     * @param workflow 审批流程
     * @param finalStatus 最终状态（APPROVED/REJECTED/CANCELLED）
     */
    private void completeWorkflow(ApprovalWorkflow workflow, ApprovalStatus finalStatus) {
        // 保存最终审批人ID，用于审计追溯
        Long finalApproverId = workflow.getCurrentApproverId();

        workflow.setStatus(finalStatus);
        workflow.setCompleteTime(LocalDateTime.now());
        workflow.setCurrentApproverId(null);
        updateById(workflow);
        sendWorkflowCompleteNotification(workflow, finalStatus);

        // 发布审批完成事件，由各业务模块监听处理
        log.info("发布审批完成事件: workflowId={}, type={}, status={}, finalApproverId={}",
                workflow.getId(), workflow.getWorkflowType(), finalStatus, finalApproverId);
        eventPublisher.publishEvent(new ApprovalCompletedEvent(this, workflow, finalStatus, finalApproverId));
    }

    // ==================== 通知 ====================

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

    // ==================== 其他 ====================

    private String generateWorkflowName(WorkflowType workflowType, String businessKey) {
        String prefix = switch (workflowType) {
            case BATCH -> "批量支付审批";
            case ADHOC -> "临时支付审批";
            case OFFLINE -> "离线员工审批";
            case PERMISSION -> "权限授权审批";
        };
        return prefix + "-" + businessKey;
    }

    @SuppressWarnings("unchecked")
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
