package com.yiyundao.compensation.service;

import com.yiyundao.compensation.interfaces.adapter.OrganizationAdapter;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeVO;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yiyundao.compensation.modules.user.service.UserBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationSyncServiceTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private IntegrationConfigService integrationConfigService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private UserBindingService userBindingService;

    private OrganizationSyncService organizationSyncService;

    @BeforeEach
    void setUp() {
        organizationSyncService = new OrganizationSyncService(
                Collections.<OrganizationAdapter>emptyList(),
                notificationService,
                integrationConfigService,
                employeeService,
                userBindingService
        );
    }

    @Test
    void importOneShouldLookupExistingEmployeeByProviderAndSubjectId() {
        Employee incoming = new Employee();
        incoming.setProvider("wechat");
        incoming.setSubjectId("wx_user_1001");
        incoming.setName("张三");

        Employee existing = new Employee();
        existing.setId(11L);

        EmployeeVO updatedVo = new EmployeeVO();
        updatedVo.setId(11L);

        Employee target = new Employee();
        target.setId(11L);

        when(employeeService.getByProviderAndSubjectId("wechat", "wx_user_1001")).thenReturn(existing);
        when(employeeService.updateEmployee(any(Long.class), any(Employee.class))).thenReturn(updatedVo);
        when(employeeService.getById(11L)).thenReturn(target);

        Employee result = organizationSyncService.importOne(incoming, "zhangsan");

        assertSame(target, result);
        verify(employeeService).getByProviderAndSubjectId("wechat", "wx_user_1001");
        verify(employeeService).updateEmployee(any(Long.class), any(Employee.class));
        verify(employeeService, never()).createEmployee(any(Employee.class));
        verify(userBindingService).ensureUserForEmployee(target, "zhangsan");
    }

    @Test
    void importOneShouldCreateEmployeeWhenProviderSubjectNotFound() {
        Employee incoming = new Employee();
        incoming.setProvider("feishu");
        incoming.setSubjectId("fs_user_2001");
        incoming.setName("李四");

        EmployeeVO createdVo = new EmployeeVO();
        createdVo.setId(21L);

        Employee target = new Employee();
        target.setId(21L);

        when(employeeService.getByProviderAndSubjectId("feishu", "fs_user_2001")).thenReturn(null);
        when(employeeService.createEmployee(incoming)).thenReturn(createdVo);
        when(employeeService.getById(21L)).thenReturn(target);

        Employee result = organizationSyncService.importOne(incoming, null);

        assertSame(target, result);
        verify(employeeService).getByProviderAndSubjectId("feishu", "fs_user_2001");
        verify(employeeService).createEmployee(incoming);
        verify(userBindingService).ensureUserForEmployee(target, null);
    }

    @Test
    void importOneShouldUpdateExistingEmployeeByEmployeeNoWhenPlatformSubjectChanged() {
        Employee incoming = new Employee();
        incoming.setProvider("wechat");
        incoming.setSubjectId("wx_user_new");
        incoming.setEmployeeId("EMP1001");
        incoming.setName("张三");

        Employee existing = new Employee();
        existing.setId(11L);
        existing.setEmployeeId("EMP1001");

        EmployeeVO updatedVo = new EmployeeVO();
        updatedVo.setId(11L);

        Employee target = new Employee();
        target.setId(11L);

        when(employeeService.getByProviderAndSubjectId("wechat", "wx_user_new")).thenReturn(null);
        when(employeeService.getByEmployeeId("EMP1001")).thenReturn(existing);
        when(employeeService.updateEmployee(any(Long.class), any(Employee.class))).thenReturn(updatedVo);
        when(employeeService.getById(11L)).thenReturn(target);

        Employee result = organizationSyncService.importOne(incoming, "zhangsan");

        assertSame(target, result);
        verify(employeeService).getByProviderAndSubjectId("wechat", "wx_user_new");
        verify(employeeService).getByEmployeeId("EMP1001");
        ArgumentCaptor<Employee> updateCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeService).updateEmployee(any(Long.class), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getProvider()).isEqualTo("wechat");
        assertThat(updateCaptor.getValue().getSubjectId()).isEqualTo("wx_user_new");
        verify(employeeService, never()).createEmployee(any(Employee.class));
        verify(userBindingService).ensureUserForEmployee(target, "zhangsan");
    }

    @Test
    void importOneShouldPassBlankDepartmentToClearStaleOrganizationMembership() {
        Employee incoming = new Employee();
        incoming.setProvider("wechat");
        incoming.setSubjectId("wx_user_clear_dept");
        incoming.setDepartment("");
        incoming.setName("孙八");

        Employee existing = new Employee();
        existing.setId(31L);
        existing.setDepartment("旧部门");

        EmployeeVO updatedVo = new EmployeeVO();
        updatedVo.setId(31L);

        Employee target = new Employee();
        target.setId(31L);

        when(employeeService.getByProviderAndSubjectId("wechat", "wx_user_clear_dept")).thenReturn(existing);
        when(employeeService.updateEmployee(any(Long.class), any(Employee.class))).thenReturn(updatedVo);
        when(employeeService.getById(31L)).thenReturn(target);

        Employee result = organizationSyncService.importOne(incoming, null);

        assertSame(target, result);
        ArgumentCaptor<Employee> updateCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeService).updateEmployee(any(Long.class), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getDepartment()).isEmpty();
        verify(userBindingService).ensureUserForEmployee(target, null);
    }

    @Test
    void importOneShouldNotCreateDuplicateWhenExistingEmployeeUpdateFails() {
        Employee incoming = new Employee();
        incoming.setProvider("wechat");
        incoming.setSubjectId("wx_user_1001");
        incoming.setName("张三");

        Employee existing = new Employee();
        existing.setId(11L);

        when(employeeService.getByProviderAndSubjectId("wechat", "wx_user_1001")).thenReturn(existing);
        when(employeeService.updateEmployee(any(Long.class), any(Employee.class)))
                .thenThrow(new IllegalStateException("update failed"));

        assertThrows(IllegalStateException.class, () -> organizationSyncService.importOne(incoming, "zhangsan"));

        verify(employeeService).getByProviderAndSubjectId("wechat", "wx_user_1001");
        verify(employeeService).updateEmployee(any(Long.class), any(Employee.class));
        verify(employeeService, never()).createEmployee(any(Employee.class));
        verify(userBindingService, never()).ensureUserForEmployee(any(), any());
    }

    @Test
    void sendNotificationShouldNormalizeWeComAliasToWechatAdapter() {
        OrganizationAdapter wechatAdapter = mock(OrganizationAdapter.class);
        when(wechatAdapter.getPlatformType()).thenReturn("wechat");
        OrganizationSyncService service = new OrganizationSyncService(
                List.of(wechatAdapter),
                notificationService,
                integrationConfigService,
                employeeService,
                userBindingService
        );

        service.sendNotification("wecom", "wx_user_9001", "审批提醒");

        verify(wechatAdapter).sendApprovalNotification("wx_user_9001", "审批提醒");
        verify(notificationService, never()).sendFallbackNotification(any(), any(), any());
    }
}
