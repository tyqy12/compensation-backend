package com.yiyundao.compensation.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.infrastructure.dao.SysRoleMapper;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.dto.UserPlatformBindingResult;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBindingServiceImplTest {

    @Mock
    private SysUserService sysUserService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private UserRoleService userRoleService;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private ExternalIdentityService externalIdentityService;
    @Mock
    private SysConfigService sysConfigService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApprovalWorkflowMapper approvalWorkflowMapper;

    @Test
    void executeApprovedPlatformLinkShouldReassignConflictingIdentityAndEmployee() {
        UserBindingServiceImpl service = newService();
        SysUser targetUser = new SysUser();
        targetUser.setId(1003L);
        targetUser.setEmployeeId(2003L);
        Employee targetEmployee = new Employee();
        targetEmployee.setId(2003L);
        ExternalIdentity occupiedIdentity = new ExternalIdentity();
        occupiedIdentity.setProvider("wechat");
        occupiedIdentity.setTenantKey("tenant-a");
        occupiedIdentity.setSubjectType("platform_user_id");
        occupiedIdentity.setSubjectId("wx-conflict");
        occupiedIdentity.setUserId(9003L);
        occupiedIdentity.setEmployeeId(8003L);

        when(sysUserService.getById(1003L)).thenReturn(targetUser);
        when(employeeService.getById(2003L)).thenReturn(targetEmployee);
        when(externalIdentityService.findActiveIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-conflict"
        )).thenReturn(occupiedIdentity);
        when(sysUserService.update(any(UpdateWrapper.class))).thenReturn(true);

        service.executeApprovedPlatformLink(7003L, 1003L, 2003L, "wechat", "wx-conflict");

        verify(externalIdentityService).deactivatePlatformIdentity(
                "wechat",
                "tenant-a",
                "platform_user_id",
                "wx-conflict",
                8003L,
                9003L,
                "approval:7003"
        );
        ArgumentCaptor<UpdateWrapper<SysUser>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(sysUserService, org.mockito.Mockito.times(2)).update(wrapperCaptor.capture());
        UpdateWrapper<SysUser> unlinkWrapper = wrapperCaptor.getAllValues().get(0);
        unlinkWrapper.getSqlSegment();
        assertThat(unlinkWrapper.getExpression().getNormal().getSqlSegment())
                .contains("employee_id =", "id <>");
        assertThat(unlinkWrapper.getSqlSet()).contains("employee_id");
        assertThat(wrapperCaptor.getAllValues().get(1).getParamNameValuePairs().values())
                .contains(2003L);
        verify(externalIdentityService).upsertPlatformIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-conflict",
                2003L,
                1003L,
                "approval:7003",
                true
        );
    }

    @Test
    void bindPlatformShouldCaptureOccupiedIdentitySnapshotWhenStartingApproval() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ApprovalEngine> approvalEngineProvider = mock(ObjectProvider.class);
        ApprovalEngine approvalEngine = mock(ApprovalEngine.class);
        UserBindingServiceImpl service = newService(approvalEngineProvider);
        SysUser targetUser = new SysUser();
        targetUser.setId(1004L);
        targetUser.setEmployeeId(2004L);
        ExternalIdentity occupiedIdentity = new ExternalIdentity();
        occupiedIdentity.setProvider("wechat");
        occupiedIdentity.setTenantKey(ExternalIdentityService.DEFAULT_TENANT_KEY);
        occupiedIdentity.setSubjectType(ExternalIdentityService.DEFAULT_SUBJECT_TYPE);
        occupiedIdentity.setSubjectId("wx-conflict");
        occupiedIdentity.setEmployeeId(8004L);
        occupiedIdentity.setUserId(9004L);

        when(sysUserService.getById(1004L)).thenReturn(targetUser);
        when(sysUserService.getById(9004L)).thenReturn(new SysUser());
        when(externalIdentityService.findActiveIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-conflict"
        )).thenReturn(occupiedIdentity);
        when(sysConfigService.getLong("system.admin_user_id", 1L)).thenReturn(1L);
        when(approvalEngineProvider.getObject()).thenReturn(approvalEngine);
        when(approvalEngine.startWorkflow(
                eq(WorkflowType.PLATFORM_BIND),
                anyString(),
                eq("PLATFORM_LINK"),
                eq(1L),
                anyMap()
        )).thenReturn(7104L);

        UserPlatformBindingResult result = service.bindPlatform(1004L, "wechat", "wx-conflict");

        assertThat(result.pendingApproval()).isTrue();
        assertThat(result.workflowId()).isEqualTo(7104L);
        assertThat(result.message()).contains("平台账号冲突");

        verify(approvalEngine).startWorkflow(
                eq(WorkflowType.PLATFORM_BIND),
                anyString(),
                eq("PLATFORM_LINK"),
                eq(1L),
                org.mockito.ArgumentMatchers.argThat(data ->
                        Long.valueOf(8004L).equals(data.get("snapshotOccupiedEmployeeId"))
                                && Long.valueOf(9004L).equals(data.get("snapshotOccupiedUserId"))
                                && "wechat".equals(data.get("snapshotOccupiedProvider"))
                                && "wx-conflict".equals(data.get("snapshotOccupiedSubjectId")))
        );
    }

    @Test
    void bindPlatformShouldCaptureBoundUserSnapshotWhenStartingEmployeeConflictApproval() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ApprovalEngine> approvalEngineProvider = mock(ObjectProvider.class);
        ApprovalEngine approvalEngine = mock(ApprovalEngine.class);
        UserBindingServiceImpl service = newService(approvalEngineProvider);
        SysUser targetUser = new SysUser();
        targetUser.setId(1006L);
        Employee employee = new Employee();
        employee.setId(2006L);
        SysUser boundUser = new SysUser();
        boundUser.setId(9006L);
        boundUser.setEmployeeId(2006L);

        when(sysUserService.getById(1006L)).thenReturn(targetUser);
        when(employeeService.getByProviderAndSubjectId("wechat", "wx-employee")).thenReturn(employee);
        when(sysUserService.getOne(org.mockito.ArgumentMatchers.any())).thenReturn(boundUser);
        when(sysConfigService.getLong("system.admin_user_id", 1L)).thenReturn(1L);
        when(approvalEngineProvider.getObject()).thenReturn(approvalEngine);
        when(approvalEngine.startWorkflow(
                eq(WorkflowType.PLATFORM_BIND),
                anyString(),
                eq("PLATFORM_LINK"),
                eq(1L),
                anyMap()
        )).thenReturn(7106L);

        UserPlatformBindingResult result = service.bindPlatform(1006L, "wechat", "wx-employee");

        assertThat(result.pendingApproval()).isTrue();
        assertThat(result.workflowId()).isEqualTo(7106L);
        assertThat(result.message()).contains("员工关联冲突");

        verify(approvalEngine).startWorkflow(
                eq(WorkflowType.PLATFORM_BIND),
                anyString(),
                eq("PLATFORM_LINK"),
                eq(1L),
                org.mockito.ArgumentMatchers.argThat(data ->
                        Long.valueOf(9006L).equals(data.get("snapshotBoundUserId")))
        );
    }

    @Test
    void bindPlatformShouldRejectWhenTargetUserAlreadyLinkedToOtherEmployee() {
        UserBindingServiceImpl service = newService();
        SysUser targetUser = new SysUser();
        targetUser.setId(1009L);
        targetUser.setEmployeeId(2009L);
        Employee matchedEmployee = new Employee();
        matchedEmployee.setId(2010L);

        when(sysUserService.getById(1009L)).thenReturn(targetUser);
        when(employeeService.getByProviderAndSubjectId("wechat", "wx-2010")).thenReturn(matchedEmployee);

        assertThatThrownBy(() -> service.bindPlatform(1009L, "wechat", "wx-2010"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已绑定其他员工");

        verify(sysUserService, never()).updateById(any(SysUser.class));
        verify(externalIdentityService, never()).upsertPlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void bindPlatformShouldRejectTargetUserEmployeeConflictBeforeStartingEmployeeConflictApproval() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ApprovalEngine> approvalEngineProvider = mock(ObjectProvider.class);
        UserBindingServiceImpl service = newService(approvalEngineProvider);
        SysUser targetUser = new SysUser();
        targetUser.setId(1013L);
        targetUser.setEmployeeId(2013L);
        Employee matchedEmployee = new Employee();
        matchedEmployee.setId(2014L);

        when(sysUserService.getById(1013L)).thenReturn(targetUser);
        when(employeeService.getByProviderAndSubjectId("wechat", "wx-2014")).thenReturn(matchedEmployee);

        assertThatThrownBy(() -> service.bindPlatform(1013L, "wechat", "wx-2014"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已绑定其他员工");

        verify(sysUserService, never()).getOne(org.mockito.ArgumentMatchers.any());
        verify(approvalEngineProvider, never()).getObject();
        verify(sysUserService, never()).updateById(any(SysUser.class));
    }

    @Test
    void bindEmployeeShouldRejectWhenTargetUserAlreadyLinkedToOtherEmployee() {
        UserBindingServiceImpl service = newService();
        SysUser targetUser = new SysUser();
        targetUser.setId(1010L);
        targetUser.setEmployeeId(2010L);
        Employee targetEmployee = new Employee();
        targetEmployee.setId(2011L);

        when(sysUserService.getById(1010L)).thenReturn(targetUser);
        when(employeeService.getById(2011L)).thenReturn(targetEmployee);

        assertThatThrownBy(() -> service.bindEmployee(1010L, 2011L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已绑定其他员工");

        verify(sysUserService, never()).getOne(org.mockito.ArgumentMatchers.any());
        verify(sysUserService, never()).updateById(any(SysUser.class));
        verify(externalIdentityService, never()).upsertPlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void executeApprovedPlatformLinkShouldRejectWhenOccupiedIdentityChangedAfterApprovalStarted() throws Exception {
        UserBindingServiceImpl service = newService();
        SysUser targetUser = new SysUser();
        targetUser.setId(1005L);
        targetUser.setEmployeeId(2005L);
        Employee targetEmployee = new Employee();
        targetEmployee.setId(2005L);
        ExternalIdentity changedIdentity = new ExternalIdentity();
        changedIdentity.setProvider("wechat");
        changedIdentity.setTenantKey(ExternalIdentityService.DEFAULT_TENANT_KEY);
        changedIdentity.setSubjectType(ExternalIdentityService.DEFAULT_SUBJECT_TYPE);
        changedIdentity.setSubjectId("wx-conflict");
        changedIdentity.setEmployeeId(8105L);
        changedIdentity.setUserId(9105L);
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(7005L);
        workflow.setWorkflowData("""
                {
                  "snapshotOccupiedEmployeeId": 8005,
                  "snapshotOccupiedUserId": 9005,
                  "snapshotOccupiedProvider": "wechat",
                  "snapshotOccupiedSubjectId": "wx-conflict"
                }
                """);

        when(sysUserService.getById(1005L)).thenReturn(targetUser);
        when(approvalWorkflowMapper.selectById(7005L)).thenReturn(workflow);
        when(externalIdentityService.findActiveIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-conflict"
        )).thenReturn(changedIdentity);

        assertThatThrownBy(() -> service.executeApprovedPlatformLink(7005L, 1005L, 2005L, "wechat", "wx-conflict"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("平台账号占用关系已变更");

        verify(employeeService, never()).getById(anyLong());
        verify(externalIdentityService, never()).deactivatePlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), any(), any(), anyString());
        verify(externalIdentityService, never()).upsertPlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void executeApprovedPlatformLinkShouldRejectWhenTargetUserEmployeeChangedAfterApprovalStarted() {
        UserBindingServiceImpl service = newService();
        SysUser targetUser = new SysUser();
        targetUser.setId(1012L);
        targetUser.setEmployeeId(2999L);
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(7012L);
        workflow.setWorkflowData("""
                {
                  "snapshotTargetUserEmployeeId": 2012
                }
                """);

        when(sysUserService.getById(1012L)).thenReturn(targetUser);
        when(approvalWorkflowMapper.selectById(7012L)).thenReturn(workflow);

        assertThatThrownBy(() -> service.executeApprovedPlatformLink(7012L, 1012L, 2012L, "wechat", "wx-2012"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户员工关联已变更");

        verify(employeeService, never()).getById(anyLong());
        verify(sysUserService, never()).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
        verify(externalIdentityService, never()).upsertPlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void executeApprovedPlatformLinkShouldRejectWhenTargetUserAlreadyLinkedToDifferentEmployeeEvenWithoutSnapshot() {
        UserBindingServiceImpl service = newService();
        SysUser targetUser = new SysUser();
        targetUser.setId(1014L);
        targetUser.setEmployeeId(2999L);

        when(sysUserService.getById(1014L)).thenReturn(targetUser);

        assertThatThrownBy(() -> service.executeApprovedPlatformLink(7014L, 1014L, 2014L, "wechat", "wx-2014"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标用户员工关联已变更");

        verify(employeeService, never()).getById(anyLong());
        verify(sysUserService, never()).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
        verify(externalIdentityService, never()).upsertPlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void executeApprovedPlatformLinkShouldRejectWhenEmployeeBindingChangedAfterApprovalStarted() {
        UserBindingServiceImpl service = newService();
        SysUser targetUser = new SysUser();
        targetUser.setId(1007L);
        targetUser.setEmployeeId(null);
        Employee employee = new Employee();
        employee.setId(2007L);
        SysUser currentBoundUser = new SysUser();
        currentBoundUser.setId(9107L);
        currentBoundUser.setEmployeeId(2007L);
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(7007L);
        workflow.setWorkflowData("""
                {
                  "snapshotBoundUserId": 9007
                }
                """);

        when(sysUserService.getById(1007L)).thenReturn(targetUser);
        when(approvalWorkflowMapper.selectById(7007L)).thenReturn(workflow);
        when(employeeService.getById(2007L)).thenReturn(employee);
        when(sysUserService.getOne(org.mockito.ArgumentMatchers.any())).thenReturn(currentBoundUser);

        assertThatThrownBy(() -> service.executeApprovedPlatformLink(7007L, 1007L, 2007L, "wechat", "wx-employee"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("员工绑定关系已变更");

        verify(sysUserService, never()).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
        verify(externalIdentityService, never()).upsertPlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void unbindPlatformShouldClearEmployeeLinkWhenRequested() {
        UserBindingServiceImpl service = newService();
        SysUser user = new SysUser();
        user.setId(1001L);
        user.setEmployeeId(2001L);
        ExternalIdentity identity = new ExternalIdentity();
        identity.setProvider("wechat");
        identity.setTenantKey("tenant-a");
        identity.setSubjectType("platform_user_id");
        identity.setSubjectId("wx-1001");

        when(sysUserService.getById(1001L)).thenReturn(user);
        when(externalIdentityService.findPrimaryByUserId(1001L)).thenReturn(identity);

        service.unbindPlatform(1001L, true);

        verify(externalIdentityService).deactivatePlatformIdentity(
                "wechat",
                "tenant-a",
                "platform_user_id",
                "wx-1001",
                2001L,
                1001L,
                "manual"
        );
        ArgumentCaptor<UpdateWrapper<SysUser>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(sysUserService).update(wrapperCaptor.capture());
        UpdateWrapper<SysUser> wrapper = wrapperCaptor.getValue();
        wrapper.getSqlSegment();
        assertThat(wrapper.getExpression().getNormal().getSqlSegment())
                .contains("id =", "employee_id =");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(1001L, 2001L);
        assertThat(wrapper.getSqlSet()).contains("employee_id");
    }

    @Test
    void unbindPlatformShouldKeepEmployeeLinkWhenNotRequested() {
        UserBindingServiceImpl service = newService();
        SysUser user = new SysUser();
        user.setId(1002L);
        user.setEmployeeId(2002L);

        when(sysUserService.getById(1002L)).thenReturn(user);

        service.unbindPlatform(1002L, false);

        verify(sysUserService, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
    }

    @Test
    void unbindPlatformShouldNotDeactivateEmployeeIdentityOwnedByAnotherUser() {
        UserBindingServiceImpl service = newService();
        SysUser user = new SysUser();
        user.setId(1008L);
        user.setEmployeeId(2008L);
        ExternalIdentity employeeIdentity = new ExternalIdentity();
        employeeIdentity.setProvider("wechat");
        employeeIdentity.setTenantKey("tenant-a");
        employeeIdentity.setSubjectType("platform_user_id");
        employeeIdentity.setSubjectId("wx-2008");
        employeeIdentity.setEmployeeId(2008L);
        employeeIdentity.setUserId(9008L);

        when(sysUserService.getById(1008L)).thenReturn(user);
        when(externalIdentityService.findPrimaryByUserId(1008L)).thenReturn(null);
        when(externalIdentityService.findPrimaryByEmployeeId(2008L)).thenReturn(employeeIdentity);

        service.unbindPlatform(1008L, true);

        verify(externalIdentityService, never()).deactivatePlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), any(), any(), anyString());
        verify(sysUserService).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
    }

    @Test
    void ensureUserForEmployeeShouldRejectExternalIdentityUserLinkedToOtherEmployee() {
        UserBindingServiceImpl service = newService();
        Employee employee = new Employee();
        employee.setId(2010L);
        ExternalIdentity identity = new ExternalIdentity();
        identity.setUserId(1010L);
        identity.setEmployeeId(2010L);
        SysUser existingUser = new SysUser();
        existingUser.setId(1010L);
        existingUser.setEmployeeId(9999L);

        when(externalIdentityService.findPrimaryByEmployeeId(2010L)).thenReturn(identity);
        when(sysUserService.getOne(org.mockito.ArgumentMatchers.any())).thenReturn(null);
        when(sysUserService.getById(1010L)).thenReturn(existingUser);

        assertThatThrownBy(() -> service.ensureUserForEmployee(employee))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("外部身份已绑定其他员工");

        verify(sysUserService, never()).updateById(any(SysUser.class));
        verify(externalIdentityService, never()).upsertPlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void ensureUserForEmployeeShouldSynchronizeExistingUserProfile() {
        UserBindingServiceImpl service = newService();
        Employee employee = new Employee();
        employee.setId(2012L);
        employee.setName("新姓名");
        employee.setPhone("13800138000");
        employee.setEmail("new@example.com");
        SysUser existingUser = new SysUser();
        existingUser.setId(1012L);
        existingUser.setEmployeeId(2012L);
        existingUser.setRealName("旧姓名");
        existingUser.setPhone("13900139000");
        existingUser.setEmail("old@example.com");

        when(sysUserService.getOne(org.mockito.ArgumentMatchers.any()))
                .thenReturn(existingUser)
                .thenReturn(null);
        when(sysUserService.updateById(existingUser)).thenReturn(true);

        service.ensureUserForEmployee(employee);

        assertThat(existingUser.getRealName()).isEqualTo("新姓名");
        assertThat(existingUser.getPhone()).isEqualTo("13800138000");
        assertThat(existingUser.getEmail()).isEqualTo("new@example.com");
        verify(sysUserService).updateById(existingUser);
    }

    @Test
    void ensureUserForEmployeeShouldRejectUnsavedEmployee() {
        UserBindingServiceImpl service = newService();
        Employee employee = new Employee();
        employee.setEmployeeId("E-UNSAVED");
        employee.setName("未落库员工");

        assertThatThrownBy(() -> service.ensureUserForEmployee(employee))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("员工未持久化");

        verify(sysUserService, never()).save(any(SysUser.class));
        verify(externalIdentityService, never()).upsertPlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void ensureUserForEmployeeShouldRejectEmployeeAlreadyLinkedToOtherUser() {
        UserBindingServiceImpl service = newService();
        Employee employee = new Employee();
        employee.setId(2011L);
        employee.setProvider("wechat");
        employee.setSubjectId("wx-2011");
        SysUser platformUser = new SysUser();
        platformUser.setId(1011L);
        SysUser boundUser = new SysUser();
        boundUser.setId(9011L);
        boundUser.setEmployeeId(2011L);

        when(externalIdentityService.findPrimaryByEmployeeId(2011L)).thenReturn(null);
        when(sysUserService.getOne(org.mockito.ArgumentMatchers.any()))
                .thenReturn(null, boundUser);
        when(sysUserService.findByPlatform("wechat", "wx-2011")).thenReturn(platformUser);

        assertThatThrownBy(() -> service.ensureUserForEmployee(employee))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("员工已绑定其他用户");

        verify(sysUserService, never()).updateById(any(SysUser.class));
        verify(externalIdentityService, never()).upsertPlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    private UserBindingServiceImpl newService() {
        @SuppressWarnings("unchecked")
        ObjectProvider<com.yiyundao.compensation.modules.approval.service.ApprovalEngine> approvalEngineProvider =
                mock(ObjectProvider.class);
        return newService(approvalEngineProvider);
    }

    private UserBindingServiceImpl newService(
            ObjectProvider<com.yiyundao.compensation.modules.approval.service.ApprovalEngine> approvalEngineProvider) {
        return new UserBindingServiceImpl(
                sysUserService,
                employeeService,
                userRoleService,
                roleMapper,
                externalIdentityService,
                sysConfigService,
                passwordEncoder,
                approvalEngineProvider,
                new ObjectMapper(),
                approvalWorkflowMapper
        );
    }
}
