package com.yiyundao.compensation.modules.employee.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformResult;
import com.yiyundao.compensation.modules.user.service.LegacyPlatformFieldPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * 员工平台绑定审批处理器
 * <p>
 * 监听审批完成事件，处理审批通过后的员工平台绑定操作。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformBindApprovalHandler {

    private final EmployeeService employeeService;
    private final ObjectMapper objectMapper;
    private final LegacyPlatformFieldPolicy legacyPlatformFieldPolicy;

    /**
     * 监听审批完成事件
     * <p>
     * 当审批类型为 PLATFORM_BIND 时，根据审批结果执行相应操作：
     * - APPROVED: 执行实际的平台绑定
     * - REJECTED/其他: 记录日志，便于追溯
     * </p>
     * <p>
     * 使用 @TransactionalEventListener(phase = AFTER_COMMIT) 确保事件处理在审批事务提交后执行。
     * 使用 @Transactional(propagation = REQUIRES_NEW) 开启新事务，确保平台绑定操作的独立性。
     * </p>
     *
     * @param event 审批完成事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onApprovalCompleted(ApprovalCompletedEvent event) {
        ApprovalWorkflow workflow = event.getWorkflow();
        ApprovalStatus finalStatus = event.getFinalStatus();

        if (workflow == null) {
            log.warn("PlatformBindApprovalHandler: workflow is null, skip processing");
            return;
        }
        if (!"PLATFORM_BIND".equalsIgnoreCase(workflow.getBusinessType())) return;

        log.info("处理员工平台绑定审批: workflowId={}, status={}", workflow.getId(), finalStatus);

        try {
            if (finalStatus == ApprovalStatus.APPROVED) {
                executeBinding(workflow);
                log.info("员工平台绑定审批处理成功: workflowId={}", workflow.getId());
            } else if (finalStatus == ApprovalStatus.REJECTED) {
                log.info("员工平台绑定审批已拒绝: workflowId={}", workflow.getId());
            } else if (finalStatus == ApprovalStatus.CANCELLED) {
                log.info("员工平台绑定审批已撤销: workflowId={}", workflow.getId());
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 业务异常向上抛出，触发事务回滚
            log.error("处理员工平台绑定审批失败（业务异常）: workflowId={}", workflow.getId(), e);
            throw e;
        } catch (Exception e) {
            // 其他异常也向上抛出
            log.error("处理员工平台绑定审批失败（系统异常）: workflowId={}", workflow.getId(), e);
            throw new RuntimeException("员工平台绑定审批处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行审批通过后的绑定操作
     */
    private void executeBinding(ApprovalWorkflow workflow) {
        try {
            var data = parseWorkflowData(workflow);
            if (data == null) {
                log.warn("审批流程数据为空: workflowId={}", workflow.getId());
                return;
            }

            Long employeeId = toLong(data.get("employeeId"));
            String provider = resolveFieldWithLegacyFallback(data, workflow.getId(), "provider", "platformType");
            String subjectId = resolveFieldWithLegacyFallback(data, workflow.getId(), "subjectId", "platformUserId");

            if (employeeId == null || provider == null || subjectId == null) {
                log.warn("审批数据不完整: workflowId={}, employeeId={}, provider={}, subjectId={}",
                        workflow.getId(), employeeId, provider, subjectId);
                return;
            }

            BindPlatformResult result = employeeService.executeApprovedBinding(
                    workflow.getId(), employeeId, provider, subjectId);

            if (result.getResult().isSuccess()) {
                log.info("员工平台绑定审批执行成功: workflowId={}, employeeId={}, userId={}",
                        workflow.getId(), employeeId, result.getUserId());
            } else {
                log.warn("员工平台绑定审批执行结果非预期: workflowId={}, result={}, message={}",
                        workflow.getId(), result.getResult(), result.getMessage());
            }

        } catch (Exception e) {
            log.error("执行员工平台绑定失败: workflowId={}", workflow.getId(), e);
            throw e;
        }
    }

    /**
     * 解析审批流程数据
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> parseWorkflowData(ApprovalWorkflow workflow) {
        if (workflow.getWorkflowData() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(workflow.getWorkflowData(), java.util.Map.class);
        } catch (Exception e) {
            log.warn("解析审批流程数据失败: workflowId={}", workflow.getId());
            return null;
        }
    }

    /**
     * 安全转换为 Long
     */
    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveFieldWithLegacyFallback(java.util.Map<String, Object> data,
                                                  Long workflowId,
                                                  String preferredKey,
                                                  String legacyKey) {
        String preferred = toTrimmedString(data.get(preferredKey));
        String legacy = toTrimmedString(data.get(legacyKey));
        if (!StringUtils.hasText(preferred) && StringUtils.hasText(legacy)) {
            legacyPlatformFieldPolicy.handleLegacyWorkflowFallback(
                    "employee_platform_bind_approval",
                    workflowId,
                    preferredKey,
                    legacyKey,
                    legacy
            );
        }
        if (StringUtils.hasText(preferred)) {
            return preferred;
        }
        return StringUtils.hasText(legacy) ? legacy : null;
    }

    private String toTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
