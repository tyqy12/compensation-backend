package com.yiyundao.compensation.modules.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.enums.UserStatus;
import com.yiyundao.compensation.common.util.TransactionAfterCommitExecutor;
import com.yiyundao.compensation.enums.EmployeeStatus;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollLineMapper;
import com.yiyundao.compensation.modules.approval.config.ApprovalFlowConfigManager;
import com.yiyundao.compensation.modules.approval.entity.ApprovalStep;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityConstants;
import com.yiyundao.compensation.service.OrganizationSyncService;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    private final PayrollLineMapper payrollLineMapper;
    private final EmployeeMapper employeeMapper;
    private final ObjectProvider<OrganizationSyncService> organizationSyncServiceProvider;
    private final ObjectMapper objectMapper;
    private final SysConfigService sysConfigService;
    private final ApprovalFlowConfigManager approvalFlowConfigManager;
    private final ApplicationEventPublisher eventPublisher;
    private final ExternalIdentityService externalIdentityService;
    private final TransactionAfterCommitExecutor afterCommitExecutor;

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
            workflow.setEmployeeId(extractEmployeeId(workflowData, businessKey));
            workflow.setSubmitTime(LocalDateTime.now());

            if (workflowData != null && !workflowData.isEmpty()) {
                workflow.setWorkflowData(objectMapper.writeValueAsString(workflowData));
            }

            // Generate steps first so we can persist workflow with non-null total_steps
            List<ApprovalStep> steps = generateApprovalSteps(workflow, workflowType, workflowData);
            if (steps.isEmpty()) {
                throw new IllegalStateException("审批流程未生成任何有效审批步骤，请检查审批配置");
            }
            workflow.setTotalSteps(steps.size());
            workflow.setCurrentApproverId(steps.get(0).getApproverId());

            // Insert workflow now that all non-nullable fields are set
            save(workflow);

            for (ApprovalStep step : steps) {
                step.setWorkflowId(workflow.getId());
                approvalStepService.save(step);
            }
            sendApprovalNotification(steps.get(0));

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
        if (approverId == null) throw new IllegalArgumentException("审批人不存在");
        ensureWorkflowRoutingComplete(workflow);
        if (!Objects.equals(workflow.getCurrentApproverId(), approverId)) throw new IllegalArgumentException("无权限审批该流程");
        if (decision != ApprovalStatus.APPROVED && decision != ApprovalStatus.REJECTED) {
            throw new IllegalArgumentException("无效的审批决策: " + decision);
        }
        if (workflow.getInitiatorId() != null && workflow.getInitiatorId().equals(approverId)) {
            throw new IllegalArgumentException("发起人不能审批自己发起的流程");
        }

        ApprovalStep currentStep = approvalStepService.getCurrentStep(workflowId, workflow.getCurrentStep());
        if (currentStep == null) throw new IllegalStateException("找不到当前审批步骤");

        claimCurrentStep(workflow, currentStep, approverId, decision, comment);

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
        if (operatorId == null) throw new IllegalArgumentException("操作人不存在");
        if (workflow.getInitiatorId() == null) throw new IllegalStateException("审批流程数据不完整，请联系管理员处理");
        if (!Objects.equals(workflow.getInitiatorId(), operatorId)) throw new IllegalArgumentException("只有发起人可以撤销流程");
        if (workflow.getStatus() != ApprovalStatus.PENDING) throw new IllegalStateException("只能撤销待审批的流程");
        cancelCurrentStep(workflow, reason);
        completeWorkflow(workflow, ApprovalStatus.CANCELLED, operatorId);
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

    private void ensureWorkflowRoutingComplete(ApprovalWorkflow workflow) {
        if (workflow.getCurrentStep() == null
                || workflow.getTotalSteps() == null
                || workflow.getCurrentApproverId() == null) {
            throw new IllegalStateException("审批流程数据不完整，请联系管理员处理");
        }
    }

    // ==================== 审批步骤生成 ====================

    /**
     * 根据流程类型生成审批步骤
     */
    private List<ApprovalStep> generateApprovalSteps(ApprovalWorkflow workflow, WorkflowType workflowType,
                                                      Map<String, Object> workflowData) {
        return switch (workflowType) {
            case BATCH -> generateBatchPaymentSteps(workflow.getInitiatorId());
            case PAYROLL_DISTRIBUTION -> generatePayrollDistributionSteps(workflow.getInitiatorId(), workflowData);
            case ADHOC -> generateAdhocPaymentSteps(workflow.getInitiatorId());
            case OFFLINE -> generateOfflineEmployeeSteps(workflow.getInitiatorId());
            case PERMISSION -> generatePermissionSteps(workflow.getInitiatorId());
            case PAYROLL_DISPUTE -> generatePayrollDisputeSteps(workflow.getInitiatorId());
        };
    }

    /**
     * 生成批量支付审批步骤
     */
    private List<ApprovalStep> generateBatchPaymentSteps(Long initiatorId) {
        List<ApprovalFlowConfigManager.ApprovalStepConfig> configured = loadPayrollApprovalConfig();
        if (configured.isEmpty()) {
            configured = approvalFlowConfigManager.getDefaultSteps(WorkflowType.BATCH);
        }
        return buildStepsFromConfig(configured, initiatorId);
    }

    /**
     * 生成薪资发放审批步骤
     */
    private List<ApprovalStep> generatePayrollDistributionSteps(Long initiatorId, Map<String, Object> workflowData) {
        List<ApprovalFlowConfigManager.ApprovalStepConfig> configured = loadPayrollApprovalConfig();
        if (configured.isEmpty()) {
            configured = approvalFlowConfigManager.getDefaultSteps(WorkflowType.PAYROLL_DISTRIBUTION);
        }
        return buildStepsFromConfig(configured, initiatorId, workflowData);
    }

    /**
     * 生成临时支付审批步骤
     */
    private List<ApprovalStep> generateAdhocPaymentSteps(Long initiatorId) {
        List<ApprovalFlowConfigManager.ApprovalStepConfig> config = loadAdhocApprovalConfig();
        if (config.isEmpty()) {
            config = approvalFlowConfigManager.getDefaultSteps(WorkflowType.ADHOC);
        }
        return buildStepsFromConfig(config, initiatorId);
    }

    /**
     * 生成架构外员工审批步骤
     */
    private List<ApprovalStep> generateOfflineEmployeeSteps(Long initiatorId) {
        List<ApprovalFlowConfigManager.ApprovalStepConfig> config = loadOfflineApprovalConfig();
        if (config.isEmpty()) {
            config = approvalFlowConfigManager.getDefaultSteps(WorkflowType.OFFLINE);
        }
        return buildStepsFromConfig(config, initiatorId);
    }

    /**
     * 生成权限授权审批步骤
     */
    private List<ApprovalStep> generatePermissionSteps(Long initiatorId) {
        List<ApprovalFlowConfigManager.ApprovalStepConfig> config = loadPermissionApprovalConfig();
        if (config.isEmpty()) {
            config = approvalFlowConfigManager.getDefaultSteps(WorkflowType.PERMISSION);
        }
        return buildStepsFromConfig(config, initiatorId);
    }

    /**
     * 生成薪酬异议审批步骤
     */
    private List<ApprovalStep> generatePayrollDisputeSteps(Long initiatorId) {
        List<ApprovalFlowConfigManager.ApprovalStepConfig> config = loadPayrollDisputeApprovalConfig();
        if (config.isEmpty()) {
            config = approvalFlowConfigManager.getDefaultSteps(WorkflowType.PAYROLL_DISPUTE);
        }
        return buildStepsFromConfig(config, initiatorId);
    }

    /**
     * 从配置构建审批步骤
     */
    private List<ApprovalStep> buildStepsFromConfig(List<ApprovalFlowConfigManager.ApprovalStepConfig> configSteps,
                                                    Long initiatorId) {
        return buildStepsFromConfig(configSteps, initiatorId, null);
    }

    private List<ApprovalStep> buildStepsFromConfig(List<ApprovalFlowConfigManager.ApprovalStepConfig> configSteps,
                                                    Long initiatorId, Map<String, Object> workflowData) {
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

            List<SysUser> approvers = resolveApprovers(cfg, initiatorId, workflowData);
            if (approvers.isEmpty()) {
                if (Boolean.TRUE.equals(cfg.getOptional())) {
                    log.info("审批步骤{}({}) 未找到审批人，已跳过(可选)", stepNo, cfg.getStepName());
                    continue;
                }
                throw new IllegalStateException(String.format("审批步骤%d(%s)未配置有效审批人",
                        stepNo, StringUtils.hasText(cfg.getStepName()) ? cfg.getStepName() : "未命名步骤"));
            }

            for (SysUser approver : approvers) {
                String approverName = Optional.ofNullable(approver.getRealName()).filter(StringUtils::hasText)
                        .orElse(approver.getUsername());
                int actualStepNo = steps.size() + 1;
                String stepName = StringUtils.hasText(cfg.getStepName())
                        ? cfg.getStepName()
                        : (StringUtils.hasText(cfg.getRole()) ? cfg.getRole() : "审批步骤" + actualStepNo);
                int timeout = cfg.getTimeoutHours() != null ? cfg.getTimeoutHours() : defaultTimeout;

                steps.add(createApprovalStep(actualStepNo, stepName, approver.getId(), approverName, timeout));
            }
        }
        return steps;
    }

    private List<SysUser> resolveApprovers(ApprovalFlowConfigManager.ApprovalStepConfig cfg,
                                           Long initiatorId, Map<String, Object> workflowData) {
        if (isPayrollManagerStep(cfg, workflowData)) {
            return resolvePayrollManagerApprovers(workflowData, initiatorId);
        }
        SysUser approver = resolveApprover(cfg, initiatorId);
        return approver == null ? List.of() : List.of(approver);
    }

    private boolean isPayrollManagerStep(ApprovalFlowConfigManager.ApprovalStepConfig cfg,
                                         Map<String, Object> workflowData) {
        return cfg != null
                && cfg.getApproverId() == null
                && !StringUtils.hasText(cfg.getApproverUsername())
                && isManagerRole(cfg.getRole())
                && workflowData != null
                && workflowData.containsKey("batchId");
    }

    private boolean isManagerRole(String role) {
        if (!StringUtils.hasText(role)) {
            return false;
        }
        String normalized = role.trim();
        return SecurityConstants.ROLE_MANAGER.equalsIgnoreCase(normalized)
                || "MANAGER".equalsIgnoreCase(normalized);
    }

    /**
     * 薪资发放的部门负责人必须来自本批次员工的直属负责人关系，不能从全局角色用户中任选一人。
     * 一个批次跨多个负责人时，为每位负责人生成独立的串行步骤，确保每个覆盖范围都完成审批。
     */
    private List<SysUser> resolvePayrollManagerApprovers(Map<String, Object> workflowData, Long initiatorId) {
        Long batchId = parseLong(workflowData.get("batchId"));
        if (batchId == null) {
            throw new IllegalStateException("薪资发放审批缺少有效的 batchId");
        }

        List<PayrollLine> lines = payrollLineMapper.selectList(new QueryWrapper<PayrollLine>()
                .select("employee_id")
                .eq("batch_id", batchId));
        if (lines == null || lines.isEmpty()) {
            throw new IllegalStateException("薪资批次没有可解析审批人的工资行: " + batchId);
        }

        Set<Long> employeeIds = new HashSet<>();
        for (PayrollLine line : lines) {
            if (line == null || line.getEmployeeId() == null) {
                throw new IllegalStateException("薪资批次存在缺少员工的工资行: " + batchId);
            }
            employeeIds.add(line.getEmployeeId());
        }

        Map<Long, Employee> employeeMap = mapEmployees(employeeMapper.selectBatchIds(employeeIds));
        Set<Long> managerIds = new HashSet<>();
        for (Long employeeId : employeeIds) {
            Employee employee = employeeMap.get(employeeId);
            if (employee == null) {
                throw new IllegalStateException("薪资批次引用的员工不存在: employeeId=" + employeeId);
            }
            if (employee.getManagerId() == null) {
                throw new IllegalStateException("薪资批次员工未配置直属负责人: employeeId=" + employeeId);
            }
            managerIds.add(employee.getManagerId());
        }

        Map<Long, Employee> managerMap = mapEmployees(employeeMapper.selectBatchIds(managerIds));
        List<Long> orderedManagerIds = managerIds.stream().sorted().toList();
        List<SysUser> approvers = new ArrayList<>(orderedManagerIds.size());
        for (Long managerId : orderedManagerIds) {
            Employee manager = managerMap.get(managerId);
            if (manager == null) {
                throw new IllegalStateException("薪资批次员工的直属负责人不存在: managerId=" + managerId);
            }
            if (!EmployeeStatus.ACTIVE.getCode().equalsIgnoreCase(manager.getStatus())) {
                throw new IllegalStateException("薪资批次员工的直属负责人不在职: managerId=" + managerId);
            }

            SysUser approver = sysUserService.findByEmployeeId(managerId);
            if (!isValidApprover(approver, initiatorId)) {
                throw new IllegalStateException("薪资批次员工的直属负责人没有有效登录用户: managerId=" + managerId);
            }
            approvers.add(approver);
        }
        return approvers;
    }

    private Map<Long, Employee> mapEmployees(List<Employee> employees) {
        Map<Long, Employee> result = new HashMap<>();
        if (employees == null) {
            return result;
        }
        for (Employee employee : employees) {
            if (employee != null && employee.getId() != null) {
                result.put(employee.getId(), employee);
            }
        }
        return result;
    }

    private Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (!StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
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
    private SysUser resolveApprover(ApprovalFlowConfigManager.ApprovalStepConfig cfg, Long initiatorId) {
        if (cfg == null) {
            return null;
        }
        SysUser approver;
        if (cfg.getApproverId() != null) {
            approver = sysUserService.getById(cfg.getApproverId());
            return isValidApprover(approver, initiatorId) ? approver : null;
        }
        if (StringUtils.hasText(cfg.getApproverUsername())) {
            approver = sysUserService.findByUsername(cfg.getApproverUsername().trim());
            return isValidApprover(approver, initiatorId) ? approver : null;
        }
        if (StringUtils.hasText(cfg.getRole())) {
            approver = sysUserService.findFirstByRoleExcluding(cfg.getRole().trim(), initiatorId);
            return isValidApprover(approver, initiatorId) ? approver : null;
        }
        return null;
    }

    private boolean isValidApprover(SysUser approver, Long initiatorId) {
        return approver != null
                && approver.getId() != null
                && UserStatus.ACTIVE.equals(approver.getStatus())
                && !Objects.equals(approver.getId(), initiatorId);
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
     * 从数据库加载架构外员工审批配置
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
     * 从数据库加载薪酬异议审批配置
     */
    private List<ApprovalFlowConfigManager.ApprovalStepConfig> loadPayrollDisputeApprovalConfig() {
        return loadApprovalConfig(SecurityConstants.CONFIG_PAYROLL_DISPUTE_APPROVAL_FLOW);
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

    // ==================== 流程流转 ====================

    private void claimCurrentStep(ApprovalWorkflow workflow, ApprovalStep currentStep, Long approverId,
                                  ApprovalStatus decision, String comment) {
        LocalDateTime approveTime = LocalDateTime.now();
        UpdateWrapper<ApprovalStep> updateWrapper = new UpdateWrapper<ApprovalStep>()
                .eq("workflow_id", workflow.getId())
                .eq("step_no", workflow.getCurrentStep())
                .eq("approver_id", approverId)
                .eq("status", ApprovalStatus.PENDING)
                .set("status", decision)
                .set("approve_time", approveTime)
                .set("approve_comment", comment)
                .set(decision == ApprovalStatus.REJECTED, "reject_reason", comment);
        if (currentStep.getId() != null) {
            updateWrapper.eq("id", currentStep.getId());
        }

        if (!approvalStepService.update(updateWrapper)) {
            throw new IllegalStateException("审批步骤状态已变更，请刷新后重试");
        }

        currentStep.setStatus(decision);
        currentStep.setApproveTime(approveTime);
        currentStep.setApproveComment(comment);
        if (decision == ApprovalStatus.REJECTED) currentStep.setRejectReason(comment);
    }

    private void moveToNextStep(ApprovalWorkflow workflow) {
        int nextStepNo = workflow.getCurrentStep() + 1;
        ApprovalStep nextStep = approvalStepService.getStepByNo(workflow.getId(), nextStepNo);
        if (nextStep == null) {
            throw new IllegalStateException("找不到下一审批步骤");
        }

        if (!update(new UpdateWrapper<ApprovalWorkflow>()
                .eq("id", workflow.getId())
                .eq("status", ApprovalStatus.PENDING)
                .eq("current_step", workflow.getCurrentStep())
                .eq("current_approver_id", workflow.getCurrentApproverId())
                .set("current_step", nextStepNo)
                .set("current_approver_id", nextStep.getApproverId()))) {
            throw new IllegalStateException("审批流程状态已变更，请刷新后重试");
        }

        workflow.setCurrentStep(nextStepNo);
        workflow.setCurrentApproverId(nextStep.getApproverId());
        sendApprovalNotification(nextStep);
    }

    private void cancelCurrentStep(ApprovalWorkflow workflow, String reason) {
        LocalDateTime cancelTime = LocalDateTime.now();
        UpdateWrapper<ApprovalStep> updateWrapper = new UpdateWrapper<ApprovalStep>()
                .eq("workflow_id", workflow.getId())
                .eq("step_no", workflow.getCurrentStep())
                .eq("status", ApprovalStatus.PENDING)
                .set("status", ApprovalStatus.CANCELLED)
                .set("approve_time", cancelTime)
                .set("approve_comment", reason)
                .set("reject_reason", reason);
        if (workflow.getCurrentApproverId() != null) {
            updateWrapper.eq("approver_id", workflow.getCurrentApproverId());
        }

        if (!approvalStepService.update(updateWrapper)) {
            throw new IllegalStateException("审批步骤状态已变更，请刷新后重试");
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
        completeWorkflow(workflow, finalStatus, workflow.getCurrentApproverId());
    }

    private void completeWorkflow(ApprovalWorkflow workflow, ApprovalStatus finalStatus, Long finalApproverId) {
        // 保存最终审批人ID，用于审计追溯
        Long currentApproverId = workflow.getCurrentApproverId();
        LocalDateTime completeTime = LocalDateTime.now();

        if (!update(new UpdateWrapper<ApprovalWorkflow>()
                .eq("id", workflow.getId())
                .eq("status", ApprovalStatus.PENDING)
                .eq("current_step", workflow.getCurrentStep())
                .eq("current_approver_id", currentApproverId)
                .set("status", finalStatus)
                .set("complete_time", completeTime)
                .set("current_approver_id", null))) {
            throw new IllegalStateException("审批流程状态已变更，请刷新后重试");
        }

        workflow.setStatus(finalStatus);
        workflow.setCompleteTime(completeTime);
        workflow.setCurrentApproverId(null);
        sendWorkflowCompleteNotification(workflow, finalStatus);

        // 发布审批完成事件，由各业务模块监听处理
        log.info("发布审批完成事件: workflowId={}, type={}, status={}, finalApproverId={}",
                workflow.getId(), workflow.getWorkflowType(), finalStatus, finalApproverId);
        eventPublisher.publishEvent(new ApprovalCompletedEvent(this, workflow, finalStatus, finalApproverId));
    }

    // ==================== 通知 ====================

    private void sendApprovalNotification(ApprovalStep step) {
        afterCommitExecutor.execute(() -> doSendApprovalNotification(step));
    }

    private void doSendApprovalNotification(ApprovalStep step) {
        try {
            SysUser approver = sysUserService.getById(step.getApproverId());
            if (approver == null) return;
            ExternalIdentity identity = externalIdentityService.findPrimaryByUserId(approver.getId());
            if (identity == null || !StringUtils.hasText(identity.getSubjectId())) return;
            String message = String.format("您有新的审批任务：%s，请及时处理。", step.getStepName());
            sendPlatformNotification(
                    identity.getProvider(),
                    identity.getSubjectId(),
                    message
            );
        } catch (Exception ignored) { }
    }

    private void sendWorkflowCompleteNotification(ApprovalWorkflow workflow, ApprovalStatus finalStatus) {
        afterCommitExecutor.execute(() -> doSendWorkflowCompleteNotification(workflow, finalStatus));
    }

    private void doSendWorkflowCompleteNotification(ApprovalWorkflow workflow, ApprovalStatus finalStatus) {
        try {
            SysUser initiator = sysUserService.getById(workflow.getInitiatorId());
            if (initiator == null) return;
            ExternalIdentity identity = externalIdentityService.findPrimaryByUserId(initiator.getId());
            if (identity == null || !StringUtils.hasText(identity.getSubjectId())) return;
            String statusText = finalStatus == ApprovalStatus.APPROVED ? "审批通过" :
                              finalStatus == ApprovalStatus.REJECTED ? "审批拒绝" : "已取消";
            String message = String.format("您的审批流程 %s 已%s。", workflow.getWorkflowName(), statusText);
            sendPlatformNotification(
                    identity.getProvider(),
                    identity.getSubjectId(),
                    message
            );
        } catch (Exception ignored) { }
    }

    private void sendPlatformNotification(String provider, String subjectId, String message) {
        OrganizationSyncService organizationSyncService = organizationSyncServiceProvider.getIfAvailable();
        if (organizationSyncService == null) {
            log.debug("OrganizationSyncService 未就绪，跳过通知: provider={}, subjectId={}",
                    provider, subjectId);
            return;
        }
        organizationSyncService.sendNotification(provider, subjectId, message);
    }

    // ==================== 其他 ====================

    private String generateWorkflowName(WorkflowType workflowType, String businessKey) {
        String prefix = switch (workflowType) {
            case BATCH -> "批量支付审批";
            case PAYROLL_DISTRIBUTION -> "薪资发放审批";
            case ADHOC -> "临时支付审批";
            case OFFLINE -> "架构外员工审批";
            case PERMISSION -> "权限授权审批";
            case PAYROLL_DISPUTE -> "薪酬异议审批";
        };
        return prefix + "-" + businessKey;
    }

    private Long extractEmployeeId(Map<String, Object> workflowData, String businessKey) {
        if (workflowData != null && workflowData.get("employeeId") != null) {
            Object raw = workflowData.get("employeeId");
            if (raw instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.parseLong(String.valueOf(raw));
            } catch (Exception ignored) {
                // ignore and fall back to businessKey parsing
            }
        }

        Long employeeIdFromKey = extractEmployeeIdFromBusinessKey(businessKey);
        if (employeeIdFromKey != null) {
            return employeeIdFromKey;
        }
        return null;
    }

    private Long extractEmployeeIdFromBusinessKey(String businessKey) {
        if (!StringUtils.hasText(businessKey) || !businessKey.startsWith("EMPLOYEE-")) {
            return null;
        }
        String remain = businessKey.substring("EMPLOYEE-".length());
        if (remain.startsWith("PROFILE-")) {
            remain = remain.substring("PROFILE-".length());
        }
        int split = remain.indexOf('-');
        String idPart = split > 0 ? remain.substring(0, split) : remain;
        try {
            return Long.parseLong(idPart);
        } catch (Exception ignored) {
            return null;
        }
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
