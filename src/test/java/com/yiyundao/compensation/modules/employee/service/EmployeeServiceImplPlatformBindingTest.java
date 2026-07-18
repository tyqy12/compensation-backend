package com.yiyundao.compensation.modules.employee.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.utils.VOConverter;
import com.yiyundao.compensation.enums.WorkflowType;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.approval.service.ApprovalEngine;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformRequest;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformResult;
import com.yiyundao.compensation.modules.employee.dto.BindResult;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.impl.EmployeeServiceImpl;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.service.EncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;

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
class EmployeeServiceImplPlatformBindingTest {

    @Mock
    private SysUserService sysUserService;
    @Mock
    private ExternalIdentityService externalIdentityService;

    private TestableEmployeeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TestableEmployeeServiceImpl(sysUserService, externalIdentityService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bindPlatformShouldDeactivateCurrentIdentityWhenBindingNewAccount() {
        Employee employee = employee(2001L, "EMP2001", "张三");
        SysUser user = user(3001L, 2001L);
        ExternalIdentity currentIdentity = identity("dingtalk", "ding-old", 2001L, 3001L);
        service.employees.put(2001L, employee);

        when(sysUserService.getOne(any())).thenReturn(user);
        when(externalIdentityService.findPrimaryByEmployeeId(2001L)).thenReturn(currentIdentity);

        BindPlatformResult result = service.bindPlatform(
                2001L,
                BindPlatformRequest.builder()
                        .provider("wechat")
                        .subjectId("wx-new")
                        .build()
        );

        assertThat(result.getResult()).isEqualTo(BindResult.SUCCESS);
        assertThat(result.getUserId()).isEqualTo(3001L);
        verify(externalIdentityService).deactivatePlatformIdentity(
                "dingtalk",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "ding-old",
                2001L,
                3001L,
                "manual"
        );
        verify(externalIdentityService).upsertPlatformIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-new",
                2001L,
                3001L,
                "manual",
                true
        );
    }

    @Test
    void bindPlatformShouldBackfillUserIdWhenSameAccountAlreadyBoundToEmployee() {
        Employee employee = employee(2003L, "EMP2003", "王五");
        SysUser user = user(3003L, 2003L);
        ExternalIdentity currentIdentity = identity("wechat", "wx-existing", 2003L, null);
        service.employees.put(2003L, employee);

        when(sysUserService.getOne(any())).thenReturn(user);
        when(externalIdentityService.findPrimaryByEmployeeId(2003L)).thenReturn(currentIdentity);

        BindPlatformResult result = service.bindPlatform(
                2003L,
                BindPlatformRequest.builder()
                        .provider("wechat")
                        .subjectId("wx-existing")
                        .build()
        );

        assertThat(result.getResult()).isEqualTo(BindResult.SUCCESS);
        assertThat(result.getUserId()).isEqualTo(3003L);
        verify(externalIdentityService).upsertPlatformIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-existing",
                2003L,
                3003L,
                "manual",
                true
        );
    }

    @Test
    void bindPlatformShouldStartApprovalWhenSameEmployeeIdentityPointsToOtherUser() {
        Employee employee = employee(2004L, "EMP2004", "赵六");
        SysUser currentUser = user(3004L, 2004L);
        SysUser operator = user(1L, null);
        ExternalIdentity currentIdentity = identity("wechat", "wx-same-employee", 2004L, 9004L);
        ApprovalEngine approvalEngine = mock(ApprovalEngine.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ApprovalEngine> approvalEngineProvider = mock(ObjectProvider.class);
        service = new TestableEmployeeServiceImpl(sysUserService, externalIdentityService, approvalEngineProvider);
        service.employees.put(2004L, employee);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a"));
        when(sysUserService.getOne(any())).thenReturn(currentUser);
        when(sysUserService.findByUsername("admin")).thenReturn(operator);
        when(externalIdentityService.findPrimaryByEmployeeId(2004L)).thenReturn(currentIdentity);
        when(externalIdentityService.findActiveIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-same-employee"
        )).thenReturn(currentIdentity);
        when(approvalEngineProvider.getObject()).thenReturn(approvalEngine);
        when(approvalEngine.startWorkflow(
                eq(WorkflowType.PLATFORM_BIND),
                anyString(),
                eq("PLATFORM_BIND"),
                eq(1L),
                anyMap()
        )).thenReturn(7104L);

        BindPlatformResult result = service.bindPlatform(
                2004L,
                BindPlatformRequest.builder()
                        .provider("wechat")
                        .subjectId("wx-same-employee")
                        .build()
        );

        assertThat(result.getResult()).isEqualTo(BindResult.PENDING_APPROVAL);
        assertThat(result.getWorkflowId()).isEqualTo(7104L);
        assertThat(result.getWorkflowType()).isEqualTo("PLATFORM_BIND");
        verify(approvalEngine).startWorkflow(
                eq(WorkflowType.PLATFORM_BIND),
                anyString(),
                eq("PLATFORM_BIND"),
                eq(1L),
                org.mockito.ArgumentMatchers.argThat(data ->
                        Long.valueOf(2004L).equals(data.get("snapshotOccupiedEmployeeId"))
                                && Long.valueOf(9004L).equals(data.get("snapshotOccupiedUserId"))
                                && "wechat".equals(data.get("snapshotOccupiedProvider"))
                                && "wx-same-employee".equals(data.get("snapshotOccupiedSubjectId")))
        );
    }

    @Test
    void executeApprovedBindingShouldReassignOccupiedIdentityAndCurrentIdentity() {
        Employee employee = employee(2002L, "EMP2002", "李四");
        SysUser user = user(3002L, 2002L);
        ExternalIdentity occupiedIdentity = identity("wechat", "wx-conflict", 9002L, 8002L);
        ExternalIdentity currentIdentity = identity("feishu", "fs-old", 2002L, 3002L);
        service.employees.put(2002L, employee);

        when(sysUserService.getOne(any())).thenReturn(user);
        when(externalIdentityService.findActiveIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-conflict"
        )).thenReturn(occupiedIdentity);
        when(externalIdentityService.findPrimaryByEmployeeId(2002L)).thenReturn(currentIdentity);

        BindPlatformResult result = service.executeApprovedBinding(7002L, 2002L, "wechat", "wx-conflict");

        assertThat(result.getResult()).isEqualTo(BindResult.SUCCESS);
        assertThat(result.getUserId()).isEqualTo(3002L);
        verify(externalIdentityService).deactivatePlatformIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-conflict",
                9002L,
                8002L,
                "approval:7002"
        );
        verify(externalIdentityService).deactivatePlatformIdentity(
                "feishu",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "fs-old",
                2002L,
                3002L,
                "approval:7002"
        );
        verify(externalIdentityService).upsertPlatformIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-conflict",
                2002L,
                3002L,
                "approval:7002",
                true
        );
    }

    @Test
    void bindPlatformShouldSnapshotOccupiedUserWhenIdentityHasNoEmployee() {
        Employee employee = employee(2006L, "EMP2006", "孙八");
        SysUser currentUser = user(3006L, 2006L);
        SysUser operator = user(1L, null);
        ExternalIdentity occupiedIdentity = identity("wechat", "wx-user-only", null, 9006L);
        ApprovalEngine approvalEngine = mock(ApprovalEngine.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ApprovalEngine> approvalEngineProvider = mock(ObjectProvider.class);
        service = new TestableEmployeeServiceImpl(sysUserService, externalIdentityService, approvalEngineProvider);
        service.employees.put(2006L, employee);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a"));
        when(sysUserService.getOne(any())).thenReturn(currentUser);
        when(sysUserService.findByUsername("admin")).thenReturn(operator);
        when(externalIdentityService.findPrimaryByEmployeeId(2006L)).thenReturn(null);
        when(externalIdentityService.findActiveIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-user-only"
        )).thenReturn(occupiedIdentity);
        when(approvalEngineProvider.getObject()).thenReturn(approvalEngine);
        when(approvalEngine.startWorkflow(
                eq(WorkflowType.PLATFORM_BIND),
                anyString(),
                eq("PLATFORM_BIND"),
                eq(1L),
                anyMap()
        )).thenReturn(7106L);

        BindPlatformResult result = service.bindPlatform(
                2006L,
                BindPlatformRequest.builder()
                        .provider("wechat")
                        .subjectId("wx-user-only")
                        .build()
        );

        assertThat(result.getResult()).isEqualTo(BindResult.PENDING_APPROVAL);
        assertThat(result.getConflictInfo().getOccupiedUserId()).isEqualTo(9006L);
        verify(approvalEngine).startWorkflow(
                eq(WorkflowType.PLATFORM_BIND),
                anyString(),
                eq("PLATFORM_BIND"),
                eq(1L),
                org.mockito.ArgumentMatchers.argThat(data ->
                        data.get("snapshotOccupiedEmployeeId") == null
                                && Long.valueOf(9006L).equals(data.get("snapshotOccupiedUserId"))
                                && "wechat".equals(data.get("snapshotOccupiedProvider"))
                                && "wx-user-only".equals(data.get("snapshotOccupiedSubjectId")))
        );
    }

    @Test
    void executeApprovedBindingShouldRejectWhenOccupiedIdentityChangedAfterApprovalStarted() {
        ApprovalWorkflowMapper approvalWorkflowMapper = mock(ApprovalWorkflowMapper.class);
        service = new TestableEmployeeServiceImpl(sysUserService, externalIdentityService, mock(ObjectProvider.class),
                approvalWorkflowMapper);
        Employee employee = employee(2005L, "EMP2005", "钱七");
        ExternalIdentity changedIdentity = identity("wechat", "wx-conflict", 9005L, 8005L);
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(7005L);
        workflow.setWorkflowData("""
                {
                  "snapshotOccupiedEmployeeId": 9004,
                  "snapshotOccupiedProvider": "wechat",
                  "snapshotOccupiedSubjectId": "wx-conflict"
                }
                """);
        service.employees.put(2005L, employee);

        when(approvalWorkflowMapper.selectById(7005L)).thenReturn(workflow);
        when(externalIdentityService.findActiveIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-conflict"
        )).thenReturn(changedIdentity);

        assertThatThrownBy(() -> service.executeApprovedBinding(7005L, 2005L, "wechat", "wx-conflict"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("平台账号占用关系已变更");

        verify(sysUserService, never()).getOne(any());
        verify(externalIdentityService, never()).deactivatePlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString());
        verify(externalIdentityService, never()).upsertPlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void executeApprovedBindingShouldRejectWhenOccupiedUserChangedAfterApprovalStarted() {
        ApprovalWorkflowMapper approvalWorkflowMapper = mock(ApprovalWorkflowMapper.class);
        service = new TestableEmployeeServiceImpl(sysUserService, externalIdentityService, mock(ObjectProvider.class),
                approvalWorkflowMapper);
        Employee employee = employee(2007L, "EMP2007", "周九");
        ExternalIdentity changedIdentity = identity("wechat", "wx-user-only", null, 9007L);
        ApprovalWorkflow workflow = new ApprovalWorkflow();
        workflow.setId(7007L);
        workflow.setWorkflowData("""
                {
                  "snapshotOccupiedUserId": 9006,
                  "snapshotOccupiedProvider": "wechat",
                  "snapshotOccupiedSubjectId": "wx-user-only"
                }
                """);
        service.employees.put(2007L, employee);

        when(approvalWorkflowMapper.selectById(7007L)).thenReturn(workflow);
        when(externalIdentityService.findActiveIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-user-only"
        )).thenReturn(changedIdentity);

        assertThatThrownBy(() -> service.executeApprovedBinding(7007L, 2007L, "wechat", "wx-user-only"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("平台账号占用关系已变更");

        verify(sysUserService, never()).getOne(any());
        verify(externalIdentityService, never()).deactivatePlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString());
        verify(externalIdentityService, never()).upsertPlatformIdentity(
                anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    private static Employee employee(Long id, String employeeNo, String name) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeId(employeeNo);
        employee.setName(name);
        return employee;
    }

    private static SysUser user(Long id, Long employeeId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setEmployeeId(employeeId);
        return user;
    }

    private static ExternalIdentity identity(String provider, String subjectId, Long employeeId, Long userId) {
        ExternalIdentity identity = new ExternalIdentity();
        identity.setProvider(provider);
        identity.setTenantKey(ExternalIdentityService.DEFAULT_TENANT_KEY);
        identity.setSubjectType(ExternalIdentityService.DEFAULT_SUBJECT_TYPE);
        identity.setSubjectId(subjectId);
        identity.setEmployeeId(employeeId);
        identity.setUserId(userId);
        identity.setStatus(ExternalIdentityService.STATUS_ACTIVE);
        identity.setPrimaryFlag(true);
        return identity;
    }

    private static class TestableEmployeeServiceImpl extends EmployeeServiceImpl {
        private final Map<Long, Employee> employees = new HashMap<>();

        private TestableEmployeeServiceImpl(SysUserService sysUserService,
                                            ExternalIdentityService externalIdentityService) {
            this(sysUserService, externalIdentityService, mock(ObjectProvider.class));
        }

        private TestableEmployeeServiceImpl(SysUserService sysUserService,
                                            ExternalIdentityService externalIdentityService,
                                            ObjectProvider<ApprovalEngine> approvalEngineProvider) {
            this(sysUserService, externalIdentityService, approvalEngineProvider, mock(ApprovalWorkflowMapper.class));
        }

        private TestableEmployeeServiceImpl(SysUserService sysUserService,
                                            ExternalIdentityService externalIdentityService,
                                            ObjectProvider<ApprovalEngine> approvalEngineProvider,
                                            ApprovalWorkflowMapper approvalWorkflowMapper) {
            super(
                    mock(EncryptionService.class),
                    mock(ObjectProvider.class),
                    approvalEngineProvider,
                    sysUserService,
                    externalIdentityService,
                    approvalWorkflowMapper,
                    mock(PayrollLineService.class),
                    mock(PayrollBatchService.class),
                    mock(PayCycleService.class),
                    mock(PaymentRecordService.class),
                    mock(VOConverter.class),
                    new ObjectMapper(),
                    mock(EmployeeDepartmentService.class),
                    mock(com.yiyundao.compensation.security.DatabasePermissionService.class)
            );
        }

        @Override
        public Employee getById(java.io.Serializable id) {
            if (!(id instanceof Long longId)) {
                return null;
            }
            return employees.get(longId);
        }
    }
}
