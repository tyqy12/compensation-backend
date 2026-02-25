package com.yiyundao.compensation.modules.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * 资源授权审批处理器
 * <p>
 * 监听审批完成事件，处理审批通过后的资源授权应用。
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceApprovalHandler {

    private final RoleService roleService;
    private final UserResourceService userResourceService;
    private final ObjectMapper objectMapper;

    /**
     * 监听审批完成事件
     * <p>
     * 处理资源授权审批，审批通过后应用授权配置。
     * 使用 @TransactionalEventListener(phase = AFTER_COMMIT) 确保事件处理在审批事务提交后执行。
     * 使用 @Transactional(propagation = REQUIRES_NEW) 开启新事务，确保资源授权操作的独立性。
     * </p>
     *
     * @param event 审批完成事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public void onApprovalCompleted(ApprovalCompletedEvent event) {
        ApprovalWorkflow workflow = event.getWorkflow();
        ApprovalStatus status = event.getFinalStatus();

        if (workflow == null) {
            log.warn("ResourceApprovalHandler: workflow is null, skip processing");
            return;
        }

        boolean isRoleGrant = "RESOURCE_GRANT".equalsIgnoreCase(workflow.getBusinessType());
        boolean isUserGrant = "USER_RESOURCE_GRANT".equalsIgnoreCase(workflow.getBusinessType());

        if (!isRoleGrant && !isUserGrant) return;

        if (status != ApprovalStatus.APPROVED) {
            log.info("资源授权审批未通过，workflowId={}", workflow.getId());
            return;
        }

        try {
            Map<String, Object> data = objectMapper.readValue(workflow.getWorkflowData(), Map.class);
            String mode = String.valueOf(data.get("mode"));

            if (isRoleGrant && "ROLE".equalsIgnoreCase(mode)) {
                handleRoleResourceGrant(data);
            } else if (isUserGrant && "USER".equalsIgnoreCase(mode)) {
                handleUserResourceGrant(data);
            }

            log.info("资源授权审批处理成功: workflowId={}", workflow.getId());
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 业务异常向上抛出，触发事务回滚
            log.error("资源授权审批回调失败（业务异常）: workflowId={}", workflow.getId(), e);
            throw e;
        } catch (Exception e) {
            // 其他异常也向上抛出
            log.error("资源授权审批回调失败（系统异常）: workflowId={}", workflow.getId(), e);
            throw new RuntimeException("资源授权审批处理失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRoleResourceGrant(Map<String, Object> data) {
        Long roleId = toLong(data.get("roleId"));
        List<Integer> rids = (List<Integer>) data.get("resourceIds");
        Map<String, List<String>> actions = (Map<String, List<String>>) data.get("actions");

        if (roleId == null) {
            log.warn("角色资源授权审批数据缺失 roleId");
            return;
        }

        // 构建分配请求，调用 RoleService 的方法
        if (rids != null && !rids.isEmpty()) {
            List<Long> resourceIds = rids.stream().map(Integer::longValue).toList();

            // 构建 RoleResourceAssignRequest
            List<com.yiyundao.compensation.interfaces.dto.role.RoleResourceAssignRequest.ResourceAssignment> assignments =
                    resourceIds.stream().map(rid -> {
                        com.yiyundao.compensation.interfaces.dto.role.RoleResourceAssignRequest.ResourceAssignment a =
                                new com.yiyundao.compensation.interfaces.dto.role.RoleResourceAssignRequest.ResourceAssignment();
                        a.setResourceId(rid);
                        a.setActions(actions != null ? actions.get(String.valueOf(rid)) : null);
                        return a;
                    }).toList();

            com.yiyundao.compensation.interfaces.dto.role.RoleResourceAssignRequest request =
                    new com.yiyundao.compensation.interfaces.dto.role.RoleResourceAssignRequest();
            request.setResources(assignments);
            request.setReplaceExisting(true); // 替换模式

            roleService.assignResources(roleId, request, 1L); // 审批流程中 operatorId 默认为系统
            log.info("审批通过后应用角色资源授权: roleId={}, resourceCount={}", roleId, resourceIds.size());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleUserResourceGrant(Map<String, Object> data) {
        Long userId = toLong(data.get("userId"));
        List<Integer> rids = (List<Integer>) data.get("resourceIds");
        Map<String, List<String>> actions = (Map<String, List<String>>) data.get("actions");

        if (userId == null) {
            log.warn("用户资源授权审批数据缺失 userId");
            return;
        }

        if (rids != null && !rids.isEmpty()) {
            List<Long> resourceIds = rids.stream().map(Integer::longValue).toList();

            // 转换 actions map 的 key 类型
            Map<Long, List<String>> actionsMap = null;
            if (actions != null) {
                actionsMap = new java.util.HashMap<>();
                for (Map.Entry<String, List<String>> entry : actions.entrySet()) {
                    try {
                        actionsMap.put(Long.parseLong(entry.getKey()), entry.getValue());
                    } catch (NumberFormatException ignored) {}
                }
            }

            userResourceService.assignResources(userId, resourceIds, actionsMap, 1L);
            log.info("审批通过后应用用户资源授权: userId={}, resourceCount={}", userId, resourceIds.size());
        }
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}
