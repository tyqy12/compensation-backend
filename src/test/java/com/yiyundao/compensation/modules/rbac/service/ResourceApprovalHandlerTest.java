package com.yiyundao.compensation.modules.rbac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.event.ApprovalCompletedEvent;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceApprovalHandlerTest {

    @Mock
    private RoleService roleService;
    @Mock
    private UserResourceService userResourceService;
    @Mock
    private SysConfigService sysConfigService;

    @Test
    void shouldUseConfiguredAdminAsSystemOperatorForRoleGrant() throws Exception {
        ResourceApprovalHandler handler = new ResourceApprovalHandler(
                roleService,
                userResourceService,
                new ObjectMapper(),
                sysConfigService
        );
        when(sysConfigService.getLong("system.admin_user_id", 1L)).thenReturn(77L);

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(1001L);
        workflow.setBusinessType("RESOURCE_GRANT");
        workflow.setWorkflowData(new ObjectMapper().writeValueAsString(Map.of(
                "mode", "ROLE",
                "roleId", 9,
                "resourceIds", List.of(11, 12),
                "actions", Map.of("11", List.of("view"))
        )));

        handler.onApprovalCompleted(new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 99L));

        verify(roleService).assignResources(
                eq(9L),
                org.mockito.ArgumentMatchers.any(),
                eq(77L)
        );
    }

    @Test
    void shouldApplyUserGrantWhenSnapshotMatchesCurrentResources() throws Exception {
        ResourceApprovalHandler handler = new ResourceApprovalHandler(
                roleService,
                userResourceService,
                new ObjectMapper(),
                sysConfigService
        );
        when(sysConfigService.getLong("system.admin_user_id", 1L)).thenReturn(77L);
        when(userResourceService.getUserResources(10L)).thenReturn(Map.of(20L, Set.of("read")));

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(1002L);
        workflow.setBusinessType("USER_RESOURCE_GRANT");
        workflow.setWorkflowData(new ObjectMapper().writeValueAsString(Map.of(
                "mode", "USER",
                "userId", 10,
                "resourceIds", List.of(20, 21),
                "actions", Map.of("20", List.of("read", "write"), "21", List.of("read")),
                "snapshotPrev", List.of(Map.of(
                        "userId", 10,
                        "resourceId", 20,
                        "actionsJson", "[\"read\"]"
                ))
        )));

        handler.onApprovalCompleted(new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 99L));

        verify(userResourceService).assignResources(
                eq(10L),
                eq(List.of(20L, 21L)),
                eq(Map.of(20L, List.of("read", "write"), 21L, List.of("read"))),
                eq(77L)
        );
    }

    @Test
    void shouldRejectUserGrantWhenSnapshotNoLongerMatchesCurrentResources() throws Exception {
        ResourceApprovalHandler handler = new ResourceApprovalHandler(
                roleService,
                userResourceService,
                new ObjectMapper(),
                sysConfigService
        );
        when(userResourceService.getUserResources(10L)).thenReturn(Map.of(20L, Set.of("read", "write")));

        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(1003L);
        workflow.setBusinessType("USER_RESOURCE_GRANT");
        workflow.setWorkflowData(new ObjectMapper().writeValueAsString(Map.of(
                "mode", "USER",
                "userId", 10,
                "resourceIds", List.of(20, 21),
                "actions", Map.of("20", List.of("read", "write"), "21", List.of("read")),
                "snapshotPrev", List.of(Map.of(
                        "userId", 10,
                        "resourceId", 20,
                        "actionsJson", "[\"read\"]"
                ))
        )));

        assertThatThrownBy(() -> handler.onApprovalCompleted(
                new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 99L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("用户资源授权审批已过期");

        verify(userResourceService, never()).assignResources(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void shouldRejectRoleGrantWhenTargetRoleMissing() throws Exception {
        ResourceApprovalHandler handler = new ResourceApprovalHandler(
                roleService,
                userResourceService,
                new ObjectMapper(),
                sysConfigService
        );
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(1004L);
        workflow.setBusinessType("RESOURCE_GRANT");
        workflow.setWorkflowData(new ObjectMapper().writeValueAsString(Map.of(
                "mode", "ROLE",
                "resourceIds", List.of(11)
        )));

        assertThatThrownBy(() -> handler.onApprovalCompleted(
                new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 99L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roleId");

        verify(roleService, never()).assignResources(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void shouldRejectUserGrantWhenResourcesMissing() throws Exception {
        ResourceApprovalHandler handler = new ResourceApprovalHandler(
                roleService,
                userResourceService,
                new ObjectMapper(),
                sysConfigService
        );
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(1005L);
        workflow.setBusinessType("USER_RESOURCE_GRANT");
        workflow.setWorkflowData(new ObjectMapper().writeValueAsString(Map.of(
                "mode", "USER",
                "userId", 10,
                "resourceIds", List.of()
        )));

        assertThatThrownBy(() -> handler.onApprovalCompleted(
                new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 99L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resourceIds");

        verify(userResourceService, never()).assignResources(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void shouldRejectApprovalDataWithMismatchedMode() throws Exception {
        ResourceApprovalHandler handler = new ResourceApprovalHandler(
                roleService,
                userResourceService,
                new ObjectMapper(),
                sysConfigService
        );
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(1006L);
        workflow.setBusinessType("RESOURCE_GRANT");
        workflow.setWorkflowData(new ObjectMapper().writeValueAsString(Map.of(
                "mode", "USER",
                "roleId", 9,
                "resourceIds", List.of(11)
        )));

        assertThatThrownBy(() -> handler.onApprovalCompleted(
                new ApprovalCompletedEvent(this, workflow, ApprovalStatus.APPROVED, 99L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("有效授权模式");

        verify(roleService, never()).assignResources(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()
        );
        verify(userResourceService, never()).assignResources(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }
}
