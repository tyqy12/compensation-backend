package com.yiyundao.compensation.interfaces.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.interfaces.dto.org.OrgFetchPreviewResponse;
import com.yiyundao.compensation.interfaces.dto.org.OrgImportRequest;
import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.system.service.OrgSyncTaskService;
import com.yiyundao.compensation.modules.user.service.LegacyPlatformFieldPolicy;
import com.yiyundao.compensation.service.OrganizationSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationSyncControllerTest {

    private final OrganizationSyncService organizationSyncService = mock(OrganizationSyncService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final OrgSyncTaskService orgSyncTaskService = mock(OrgSyncTaskService.class);
    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final LegacyPlatformFieldPolicy legacyPlatformFieldPolicy = mock(LegacyPlatformFieldPolicy.class);
    private final OrganizationSyncController controller = new OrganizationSyncController(
            organizationSyncService,
            auditLogService,
            orgSyncTaskService,
            employeeService,
            legacyPlatformFieldPolicy
    );

    @Test
    void taskShouldReturnNotFoundWhenAsyncTaskMissing() {
        when(orgSyncTaskService.get("missing-task")).thenReturn(null);

        ApiResponse<OrgSyncTaskService.TaskInfo> response = controller.task("missing-task");

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("同步任务不存在");
    }

    @Test
    @SuppressWarnings("unchecked")
    void importEmployeesShouldSkipExistingEmployeeByEmployeeNoInNewOnlyMode() {
        OrgImportRequest request = new OrgImportRequest();
        request.setProvider("wechat");
        OrgImportRequest.EmployeeItem item = new OrgImportRequest.EmployeeItem();
        item.setEmployeeId("EMP1001");
        item.setName("张三");
        item.setSubjectId("wx-new");
        request.setItems(List.of(item));

        Employee existing = new Employee();
        existing.setId(11L);
        existing.setEmployeeId("EMP1001");

        when(employeeService.getByProviderAndSubjectId("wechat", "wx-new")).thenReturn(null);
        when(employeeService.getByEmployeeId("EMP1001")).thenReturn(existing);

        ApiResponse<Map<String, Object>> response = controller.importEmployees(request);

        assertThat(response.getCode()).isZero();
        Map<String, Object> data = response.getData();
        assertThat(data.get("success")).isEqualTo(0);
        assertThat(data.get("skipped")).isEqualTo(1);
        assertThat(data.get("created")).isEqualTo(0);
        assertThat(data.get("updated")).isEqualTo(0);
        List<String> skippedItems = (List<String>) data.get("skippedItems");
        assertThat(skippedItems).hasSize(1);
        assertThat(skippedItems.get(0)).contains("EMP1001");
        verify(organizationSyncService, never()).importOne(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void importEmployeesShouldRejectItemWithoutPlatformIdentity() {
        OrgImportRequest request = new OrgImportRequest();
        request.setProvider("wechat");
        OrgImportRequest.EmployeeItem item = new OrgImportRequest.EmployeeItem();
        item.setEmployeeId("EMP1002");
        item.setName("李四");
        request.setItems(List.of(item));

        ApiResponse<Map<String, Object>> response = controller.importEmployees(request);

        assertThat(response.getCode()).isZero();
        Map<String, Object> data = response.getData();
        assertThat(data.get("success")).isEqualTo(0);
        assertThat(data.get("failed")).isEqualTo(1);
        List<String> errors = (List<String>) data.get("errors");
        assertThat(errors).singleElement().asString().contains("平台用户ID不能为空");
        verify(organizationSyncService, never()).importOne(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void importEmployeesShouldRejectItemWithoutValidPlatform() {
        OrgImportRequest request = new OrgImportRequest();
        OrgImportRequest.EmployeeItem item = new OrgImportRequest.EmployeeItem();
        item.setEmployeeId("EMP1003");
        item.setName("王五");
        item.setProvider("unknown");
        item.setSubjectId("external-1003");
        request.setItems(List.of(item));

        ApiResponse<Map<String, Object>> response = controller.importEmployees(request);

        assertThat(response.getCode()).isZero();
        Map<String, Object> data = response.getData();
        assertThat(data.get("success")).isEqualTo(0);
        assertThat(data.get("failed")).isEqualTo(1);
        List<String> errors = (List<String>) data.get("errors");
        assertThat(errors).singleElement().asString().contains("平台类型无效");
        verify(organizationSyncService, never()).importOne(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fetchShouldNotMergePreviewEmployeesWhenSubjectIdIsMissing() {
        Employee first = new Employee();
        first.setProvider("wechat");
        first.setEmployeeId("EMP2001");
        first.setName("赵六");
        first.setDepartment("研发部");

        Employee second = new Employee();
        second.setProvider("wechat");
        second.setEmployeeId("EMP2002");
        second.setName("钱七");
        second.setDepartment("财务部");

        when(organizationSyncService.fetchPreview("wechat")).thenReturn(List.of(first, second));

        ApiResponse<OrgFetchPreviewResponse> response = controller.fetch("wechat", null);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getTotalEmployees()).isEqualTo(2);
        assertThat(response.getData().getEmployees())
                .extracting(com.yiyundao.compensation.interfaces.dto.org.EmployeePreviewDto::getGroupKey)
                .containsExactly("wechat:employee:EMP2001", "wechat:employee:EMP2002");
    }

    @Test
    void importEmployeesShouldClearEmployeeDepartmentRelationsWhenDepartmentsAreExplicitlyEmpty() {
        OrgImportRequest request = new OrgImportRequest();
        request.setProvider("wechat");
        request.setImportMode("upsert");
        OrgImportRequest.EmployeeItem item = new OrgImportRequest.EmployeeItem();
        item.setEmployeeId("EMP3001");
        item.setName("孙八");
        item.setSubjectId("wx-clear-dept");
        item.setDepartments(List.of());
        request.setItems(List.of(item));

        Employee existing = new Employee();
        existing.setId(31L);
        existing.setEmployeeId("EMP3001");
        Employee saved = new Employee();
        saved.setId(31L);

        when(employeeService.getByProviderAndSubjectId("wechat", "wx-clear-dept")).thenReturn(existing);
        when(organizationSyncService.importOne(any(Employee.class), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(saved);

        ApiResponse<Map<String, Object>> response = controller.importEmployees(request);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).containsEntry("success", 1).containsEntry("updated", 1);
        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(organizationSyncService).importOne(employeeCaptor.capture(), org.mockito.ArgumentMatchers.isNull());
        assertThat(employeeCaptor.getValue().getDepartment()).isEmpty();
        assertThat(employeeCaptor.getValue().getDepartments()).isEmpty();
    }

    @Test
    void historyShouldClampPageAndSizeBeforeQuerying() {
        Page<AuditLog> page = new Page<>(1, 200, 0);
        page.setRecords(List.of());
        when(auditLogService.page(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        ApiResponse<Map<String, Object>> response = controller.history(-1, 1000, null, null);

        ArgumentCaptor<Page<AuditLog>> captor = ArgumentCaptor.forClass(Page.class);
        verify(auditLogService).page(captor.capture(), any(LambdaQueryWrapper.class));
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("current", 1L).containsEntry("size", 200L);
    }
}
