package com.yiyundao.compensation.modules.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 平台绑定审批处理器
 * <p>
 * 监听审批完成事件，处理用户平台绑定和员工关联操作。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformLinkApprovalHandler {

    private final UserBindingService userBindingService;
    private final ObjectMapper objectMapper;
    private final LegacyPlatformFieldPolicy legacyPlatformFieldPolicy;

    /**
     * 监听审批完成事件
     * <p>
     * 处理平台绑定审批，审批通过后执行绑定操作。
     * 使用同步事件监听，确保绑定失败时审批事务回滚，避免审批已通过但绑定未生效。
     * </p>
     *
     * @param event 审批完成事件
     */
    @EventListener
    @SuppressWarnings("unchecked")
    public void onApprovalCompleted(ApprovalCompletedEvent event) {
        ApprovalWorkflow workflow = event.getWorkflow();
        ApprovalStatus finalStatus = event.getFinalStatus();

        if (workflow == null) {
            log.warn("PlatformLinkApprovalHandler: workflow is null, skip processing");
            return;
        }
        if (!"PLATFORM_LINK".equalsIgnoreCase(workflow.getBusinessType())) return;

        try {
            if (finalStatus == ApprovalStatus.APPROVED) {
                // 数据解析健壮性增强
                if (workflow.getWorkflowData() == null || workflow.getWorkflowData().trim().isEmpty()) {
                    log.error("平台绑定审批数据为空: workflowId={}", workflow.getId());
                    throw new IllegalStateException("审批流程数据为空，无法执行绑定操作");
                }

                Map<String, Object> data;
                try {
                    data = objectMapper.readValue(workflow.getWorkflowData(), Map.class);
                } catch (Exception e) {
                    log.error("平台绑定审批数据解析失败: workflowId={}, error={}",
                            workflow.getId(), e.getMessage(), e);
                    throw new IllegalStateException("审批流程数据解析失败: " + e.getMessage(), e);
                }

                Long userId = toLong(data.get("userId"));
                Long employeeId = toLong(data.get("employeeId"));
                String provider = resolveFieldWithLegacyFallback(
                        data, workflow.getId(), "proposedProvider", "proposedPlatformType");
                String subjectId = resolveFieldWithLegacyFallback(
                        data, workflow.getId(), "proposedSubjectId", "proposedPlatformUserId");

                // 关键字段验证
                if (userId == null) {
                    log.error("平台绑定审批缺少 userId: workflowId={}", workflow.getId());
                    throw new IllegalArgumentException("审批数据缺少必需字段: userId");
                }

                if (provider != null && subjectId != null) {
                    log.info("执行审批通过的平台绑定: workflowId={}, userId={}, employeeId={}, provider={}, subjectId={}",
                            workflow.getId(), userId, employeeId, provider, subjectId);
                    userBindingService.executeApprovedPlatformLink(workflow.getId(), userId, employeeId, provider, subjectId);
                } else if (employeeId != null) {
                    log.info("执行员工关联: workflowId={}, userId={}, employeeId={}",
                            workflow.getId(), userId, employeeId);
                    userBindingService.bindEmployee(userId, employeeId);
                } else {
                    throw new IllegalArgumentException("审批数据缺少可执行的绑定目标");
                }

                log.info("平台绑定审批处理成功: workflowId={}", workflow.getId());
            } else {
                // 审批拒绝：保留快照，便于后续人工恢复
                log.info("平台绑定审批已拒绝，workflowId={}", workflow.getId());
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 业务异常向上抛出，触发事务回滚
            log.error("处理平台绑定审批失败（业务异常）: workflowId={}", workflow.getId(), e);
            throw e;
        } catch (Exception e) {
            // 其他异常也向上抛出
            log.error("处理平台绑定审批失败（系统异常）: workflowId={}", workflow.getId(), e);
            throw new RuntimeException("平台绑定审批处理失败: " + e.getMessage(), e);
        }
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private String resolveFieldWithLegacyFallback(Map<String, Object> data,
                                                  Long workflowId,
                                                  String preferredKey,
                                                  String legacyKey) {
        String preferred = toTrimmedString(data.get(preferredKey));
        String legacy = toTrimmedString(data.get(legacyKey));
        if (!StringUtils.hasText(preferred) && StringUtils.hasText(legacy)) {
            legacyPlatformFieldPolicy.handleLegacyWorkflowFallback(
                    "user_platform_link_approval",
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
