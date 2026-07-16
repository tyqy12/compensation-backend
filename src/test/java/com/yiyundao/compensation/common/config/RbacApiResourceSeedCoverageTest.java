package com.yiyundao.compensation.common.config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class RbacApiResourceSeedCoverageTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ObjectMapper STRICT_OBJECT_MAPPER = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()
    );
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Test
    void seedAndMigrationShouldContainGoLiveSmokeApiResources() throws IOException {
        String seedSql = Files.readString(Path.of("src/main/resources/sql/seed/2025-09-29__seed_initial_resources.sql"));
        String migrationSql = Files.readString(Path.of("src/main/resources/sql/migrations/2026-06-03__missing_api_resources_for_go_live_smoke.sql"));
        String adminCoverageMigrationSql = Files.readString(Path.of("src/main/resources/sql/migrations/2026-06-04__admin_frontend_api_resource_coverage.sql"));
        String payrollOrgCoverageMigrationSql = Files.readString(Path.of("src/main/resources/sql/migrations/2026-06-04__frontend_payroll_org_api_resource_coverage.sql"));
        String employeeAliasPayrollReportMigrationSql = Files.readString(Path.of("src/main/resources/sql/migrations/2026-06-05__employee_alias_and_payroll_report_api_resources.sql"));
        String employeeSelfLegacyRoleGrantsMigrationSql = Files.readString(Path.of("src/main/resources/sql/migrations/2026-06-05__employee_self_profile_legacy_role_grants.sql"));
        String adminGovernanceCoverageMigrationSql = Files.readString(Path.of("src/main/resources/sql/migrations/2026-06-05__admin_api_resource_governance_coverage.sql"));
        String settlementProviderCoverageMigrationSql = Files.readString(Path.of("src/main/resources/sql/migrations/2026-06-05__settlement_provider_api_resource_coverage.sql"));
        String approvalReadCoverageMigrationSql = Files.readString(Path.of("src/main/resources/sql/migrations/2026-06-05__approval_read_api_resource_coverage.sql"));
        String approvalObjectScopedGrantMigrationSql = Files.readString(Path.of("src/main/resources/sql/migrations/2026-06-05__approval_object_scoped_resource_grants.sql"));
        String initialResourcesJson = Files.readString(Path.of("src/main/resources/sql/initial_resources.json"));

        assertThat(seedSql)
                .contains("api.payment.batch.precheck")
                .contains("/api/payment/batch/{batchNo}/precheck")
                .contains("api.payment.batch.retry-failed")
                .contains("/api/payment/batch/{batchNo}/retry-failed")
                .contains("api.system.org.sync-task-detail")
                .contains("/api/system/org/sync-task/{id}")
                .contains("api.admin.app-registry.detail")
                .contains("/api/admin/app-registry/{id}")
                .contains("api.admin.role.detail")
                .contains("/api/admin/roles/{id}")
                .contains("api.admin.audit.time-range")
                .contains("/api/admin/audit-logs/time-range")
                .contains("api.admin.audit.metrics")
                .contains("/api/admin/audit-logs/metrics")
                .contains("api.settlement.provider-config.list")
                .contains("/api/settlement/provider-config")
                .contains("api.settlement.routing.mapping.by-type")
                .contains("/api/settlement/routing/mapping/type/{employmentType}")
                .contains("api.admin.integration-config.enable")
                .contains("/api/admin/integration-configs/{platformType}/enable");

        assertThat(migrationSql)
                .contains("api.payment.batch.precheck")
                .contains("/api/payment/batch/{batchNo}/precheck")
                .contains("api.payment.batch.retry-failed")
                .contains("/api/payment/batch/{batchNo}/retry-failed")
                .contains("api.system.org.sync-task-detail")
                .contains("/api/system/org/sync-task/{id}")
                .contains("api.admin.app-registry.detail")
                .contains("/api/admin/app-registry/{id}")
                .contains("api.admin.integration-config.enable")
                .contains("/api/admin/integration-configs/{platformType}/enable");

        assertThat(adminCoverageMigrationSql)
                .contains("api.admin.user-bindings.list")
                .contains("/api/admin/user-bindings")
                .contains("api.admin.role.permissions.read")
                .contains("api.admin.role.permissions.revoke")
                .contains("api.admin.resource.export")
                .contains("api.admin.integration-config.test-connection");

        assertThat(payrollOrgCoverageMigrationSql)
                .contains("api.payroll.import.commit")
                .contains("/api/payroll/import/commit")
                .contains("api.payroll.distributions.items")
                .contains("api.payroll.payslips.download")
                .contains("api.payroll.reports.basic")
                .contains("/api/payroll/reports/basic/export")
                .contains("api.system.org.history")
                .contains("/api/system/org/history");

        assertThat(employeeAliasPayrollReportMigrationSql)
                .contains("api.employees.list")
                .contains("/api/employees/{id}/settlement-account")
                .contains("api.employee.unbind-platform")
                .contains("api.payroll.reports.basic")
                .contains("/api/payroll/reports/basic/export");

        assertThat(employeeSelfLegacyRoleGrantsMigrationSql)
                .contains("api.employee.me.detail")
                .contains("api.employee.me.contact-update")
                .contains("api.employee.me.change-request-create")
                .contains("api.employee.me.change-request-list")
                .contains("'EMPLOYEE'")
                .contains("'role.employee'")
                .contains("permission_version");

        assertThat(adminGovernanceCoverageMigrationSql)
                .contains("api.system.info")
                .contains("/api/system/info")
                .contains("api.admin.app-registry.rotate-secret")
                .contains("/api/admin/app-registry/{id}/rotate-secret")
                .contains("api.admin.payment-batch.cancel")
                .contains("/api/admin/payment/batch/{id}/cancel")
                .contains("api.admin.role.detail")
                .contains("/api/admin/roles/{id}")
                .contains("api.admin.audit.user-recent")
                .contains("api.admin.audit.time-range")
                .contains("api.admin.audit.metrics")
                .contains("api.admin.users.provision-from-employees")
                .contains("api.admin.integration-config.alipay-cert-upload");

        assertThat(settlementProviderCoverageMigrationSql)
                .contains("api.settlement.provider-config.list")
                .contains("api.settlement.provider-config.status")
                .contains("/api/settlement/provider-config/{id}/status")
                .contains("api.settlement.routing.mapping.list")
                .contains("api.settlement.routing.mapping.by-type")
                .contains("/api/settlement/routing/mapping/type/{employmentType}");

        assertThat(approvalReadCoverageMigrationSql)
                .contains("api.approval.workflow.list")
                .contains("/api/approval/workflows")
                .contains("api.approval.workflow.pending")
                .contains("/api/approval/workflows/pending")
                .contains("api.approval.workflow.my")
                .contains("/api/approval/workflows/my")
                .contains("api.approval.workflow.detail")
                .contains("/api/approval/workflows/{id}")
                .contains("api.approval.workflow.steps")
                .contains("/api/approval/workflows/{id}/steps");

        assertThat(approvalObjectScopedGrantMigrationSql)
                .contains("JSON_REMOVE(COALESCE(props_json, JSON_OBJECT()), '$.roles')")
                .contains("menu.approval")
                .contains("approval.workflow.detail")
                .contains("api.approval.workflow.pending")
                .contains("api.approval.workflow.approve")
                .contains("role.status = 'enabled'")
                .contains("permission_version");

        assertThat(initialResourcesJson)
                .contains("api.payment.batch.precheck")
                .contains("/api/payment/batch/{batchNo}/precheck")
                .contains("api.payment.batch.retry-failed")
                .contains("/api/payment/batch/{batchNo}/retry-failed")
                .contains("api.system.org.sync-task-detail")
                .contains("/api/system/org/sync-task/{id}")
                .contains("api.admin.integration-config.enable")
                .contains("/api/admin/integration-configs/{platformType}/enable")
                .contains("api.employee.approvals")
                .contains("/api/employee/{id}/approvals")
                .contains("api.employee.payslips")
                .contains("/api/employee/{id}/payslips")
                .contains("api.employee.payments")
                .contains("/api/employee/{id}/payments")
                .contains("api.employee.decrypt-settlement")
                .contains("/api/employee/{id}/settlement-account")
                .contains("api.employees.list")
                .contains("/api/employees/{id}/settlement-account")
                .contains("api.employee.unbind-platform")
                .contains("api.employee.me.detail")
                .contains("/api/employee/me")
                .contains("api.employee.me.contact-update")
                .contains("/api/employee/me/contact")
                .contains("api.employee.me.change-request-create")
                .contains("api.employee.me.change-request-list")
                .contains("/api/employee/me/change-requests")
                .contains("view.payroll.confirmations")
                .contains("/payroll/confirmations")
                .contains("api.payroll.confirmations.pending")
                .contains("/api/payroll/confirmations/pending")
                .contains("api.payroll.confirmations.summary")
                .contains("/api/payroll/confirmations/batches/*/summary")
                .contains("api.payroll.confirmations.confirm")
                .contains("/api/payroll/confirmations/payslips/*/confirm")
                .contains("api.payroll.confirmations.object")
                .contains("/api/payroll/confirmations/payslips/*/object")
                .contains("api.payroll.confirmations.batch-confirm")
                .contains("/api/payroll/confirmations/batches/*/batch-confirm")
                .contains("api.payroll.confirmations.assign")
                .contains("/api/payroll/confirmations/batches/*/assign")
                .contains("api.payroll.reports.basic")
                .contains("/api/payroll/reports/basic/export")
                .contains("api.system.info")
                .contains("/api/system/info")
                .contains("api.admin.role.detail")
                .contains("/api/admin/roles/{id}")
                .contains("api.admin.audit.metrics")
                .contains("api.settlement.provider-config.by-code")
                .contains("/api/settlement/provider-config/code/{providerCode}")
                .contains("api.settlement.routing.mapping.status")
                .contains("/api/settlement/routing/mapping/{id}/status")
                .contains("api.approval.workflow.cancel")
                .contains("/api/approval/workflows/{id}/cancel");

        assertThat(seedSql)
                .contains("api.approval.workflow.list")
                .contains("/api/approval/workflows")
                .contains("api.approval.workflow.pending")
                .contains("/api/approval/workflows/pending")
                .contains("api.approval.workflow.my")
                .contains("/api/approval/workflows/my")
                .contains("api.approval.workflow.detail")
                .contains("/api/approval/workflows/{id}")
                .contains("api.approval.workflow.steps")
                .contains("/api/approval/workflows/{id}/steps");
    }

    @Test
    void openApiDocsShouldMatchImplementedClientCredentialsContract() throws IOException {
        String openApiDoc = Files.readString(Path.of("docs/openapi/payroll-api-v1.md"));
        String ptFrontendGuide = Files.readString(Path.of("docs/frontend/pt-readonly-upgrade-guide.md"));
        String frontendOverview = Files.readString(Path.of("docs/frontend/payroll-upgrade-guide.md"));

        assertThat(openApiDoc)
                .contains("POST /api/v1/oauth/token")
                .contains("tokenUrl: /api/v1/oauth/token")
                .contains("业务成功码为 `code=" + ErrorCode.SUCCESS.getCode() + "`")
                .contains("Query：\n- employeeRef（必填）")
                .doesNotContain("POST /api/oauth/token")
                .doesNotContain("\"code\":200");

        assertThat(ptFrontendGuide)
                .contains("POST /api/v1/oauth/token")
                .contains("GET /api/v1/payslips/{id}?employeeRef=emp:E0001");

        assertThat(frontendOverview)
                .contains("POST /api/v1/oauth/token")
                .contains("\"code\": " + ErrorCode.SUCCESS.getCode())
                .contains("GET /api/v1/payslips/{id}?employeeRef=...");
    }

    @Test
    void initialResourcesShouldMatchFrontendPayrollApiRoutes() throws IOException {
        List<ApiResource> resources = loadInitialApiResources();

        List<RouteSample> samples = List.of(
                new RouteSample("GET", "/api/payroll/batches"),
                new RouteSample("POST", "/api/payroll/batches"),
                new RouteSample("GET", "/api/payroll/batches/1001"),
                new RouteSample("PUT", "/api/payroll/batches/1001"),
                new RouteSample("POST", "/api/payroll/batches/1001/dry-run"),
                new RouteSample("GET", "/api/payroll/batches/1001/ledger"),
                new RouteSample("GET", "/api/payroll/batches/1001/manager-review"),
                new RouteSample("POST", "/api/payroll/batches/1001/lock"),
                new RouteSample("POST", "/api/payroll/batches/1001/compute"),
                new RouteSample("POST", "/api/payroll/batches/1001/submit-approval"),
                new RouteSample("POST", "/api/payroll/batches/1001/retry-payment"),
                new RouteSample("POST", "/api/payroll/import/commit"),
                new RouteSample("GET", "/api/payroll/import/batches/1001/items"),
                new RouteSample("POST", "/api/payroll/import/batches/1001/items/manual"),
                new RouteSample("PUT", "/api/payroll/import/batches/1001/items/88"),
                new RouteSample("DELETE", "/api/payroll/import/batches/1001/items/88"),
                new RouteSample("GET", "/api/payroll/import/salary-items"),
                new RouteSample("GET", "/api/payroll/templates"),
                new RouteSample("POST", "/api/payroll/templates"),
                new RouteSample("GET", "/api/payroll/templates/7"),
                new RouteSample("PUT", "/api/payroll/templates/7"),
                new RouteSample("GET", "/api/payroll/templates/7/version"),
                new RouteSample("GET", "/api/payroll/cycles"),
                new RouteSample("POST", "/api/payroll/cycles"),
                new RouteSample("GET", "/api/payroll/cycles/open"),
                new RouteSample("GET", "/api/payroll/cycles/9"),
                new RouteSample("PUT", "/api/payroll/cycles/9"),
                new RouteSample("POST", "/api/payroll/cycles/9/advance"),
                new RouteSample("DELETE", "/api/payroll/cycles/9"),
                new RouteSample("GET", "/api/payroll/distributions"),
                new RouteSample("GET", "/api/payroll/distributions/2001"),
                new RouteSample("GET", "/api/payroll/distributions/2001/items"),
                new RouteSample("GET", "/api/payroll/distributions/2001/reconciliation"),
                new RouteSample("POST", "/api/payroll/distributions/2001/retry"),
                new RouteSample("GET", "/api/payroll/reconciliations"),
                new RouteSample("GET", "/api/payroll/reconciliations/9001"),
                new RouteSample("GET", "/api/payroll/payslips"),
                new RouteSample("GET", "/api/payroll/payslips/11"),
                new RouteSample("GET", "/api/payroll/payslips/11/download"),
                new RouteSample("GET", "/api/payroll/reports/basic"),
                new RouteSample("GET", "/api/payroll/reports/basic/export")
        );

        List<RouteSample> missing = samples.stream()
                .filter(sample -> resources.stream().noneMatch(resource -> resource.matches(sample)))
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void initialResourcesShouldMatchEmployeePluralAliasRoutes() throws IOException {
        List<ApiResource> resources = loadInitialApiResources();

        List<RouteSample> samples = List.of(
                new RouteSample("GET", "/api/employees"),
                new RouteSample("POST", "/api/employees"),
                new RouteSample("GET", "/api/employees/1001"),
                new RouteSample("PUT", "/api/employees/1001"),
                new RouteSample("GET", "/api/employees/1001/approvals"),
                new RouteSample("GET", "/api/employees/1001/payslips"),
                new RouteSample("GET", "/api/employees/1001/payments"),
                new RouteSample("PATCH", "/api/employees/1001/status"),
                new RouteSample("POST", "/api/employees/1001/bind-platform"),
                new RouteSample("DELETE", "/api/employees/1001/unbind-platform"),
                new RouteSample("DELETE", "/api/employee/1001/unbind-platform"),
                new RouteSample("GET", "/api/employees/offline"),
                new RouteSample("GET", "/api/employees/resigned"),
                new RouteSample("GET", "/api/employees/1001/id-card"),
                new RouteSample("GET", "/api/employees/1001/bank-account"),
                new RouteSample("GET", "/api/employees/1001/settlement-account"),
                new RouteSample("POST", "/api/employees/batch-import")
        );

        List<RouteSample> missing = samples.stream()
                .filter(sample -> resources.stream().noneMatch(resource -> resource.matches(sample)))
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void initialResourcesShouldMatchApprovalWorkflowRoutes() throws IOException {
        List<ApiResource> resources = loadInitialApiResources();

        List<RouteSample> samples = List.of(
                new RouteSample("GET", "/api/approval/workflows"),
                new RouteSample("GET", "/api/approval/workflows/pending"),
                new RouteSample("GET", "/api/approval/workflows/my"),
                new RouteSample("GET", "/api/approval/workflows/1001"),
                new RouteSample("GET", "/api/approval/workflows/1001/steps"),
                new RouteSample("POST", "/api/approval/workflows/1001/approve"),
                new RouteSample("POST", "/api/approval/workflows/1001/reject"),
                new RouteSample("POST", "/api/approval/workflows/1001/cancel")
        );

        List<RouteSample> missing = samples.stream()
                .filter(sample -> resources.stream().noneMatch(resource -> resource.matches(sample)))
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void initialResourcesShouldMatchFrontendOrgSyncApiRoutes() throws IOException {
        List<ApiResource> resources = loadInitialApiResources();

        List<RouteSample> samples = List.of(
                new RouteSample("GET", "/api/system/org/platforms"),
                new RouteSample("GET", "/api/system/org/check"),
                new RouteSample("GET", "/api/system/org/history"),
                new RouteSample("GET", "/api/system/org/departments/tree"),
                new RouteSample("POST", "/api/system/org/fetch-tree"),
                new RouteSample("POST", "/api/system/org/sync"),
                new RouteSample("POST", "/api/system/org/sync-async"),
                new RouteSample("POST", "/api/system/org/fetch"),
                new RouteSample("POST", "/api/system/org/import"),
                new RouteSample("GET", "/api/system/org/sync-task/sync-task-123")
        );

        List<RouteSample> missing = samples.stream()
                .filter(sample -> resources.stream().noneMatch(resource -> resource.matches(sample)))
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void initialResourcesShouldMatchSettlementProviderApiRoutes() throws IOException {
        List<ApiResource> resources = loadInitialApiResources();

        List<RouteSample> samples = List.of(
                new RouteSample("GET", "/api/settlement/provider-config"),
                new RouteSample("POST", "/api/settlement/provider-config"),
                new RouteSample("GET", "/api/settlement/provider-config/7"),
                new RouteSample("GET", "/api/settlement/provider-config/code/alipay"),
                new RouteSample("GET", "/api/settlement/provider-config/enabled"),
                new RouteSample("PUT", "/api/settlement/provider-config/7"),
                new RouteSample("DELETE", "/api/settlement/provider-config/7"),
                new RouteSample("PATCH", "/api/settlement/provider-config/7/status"),
                new RouteSample("GET", "/api/settlement/routing/mapping/type/FULL_TIME"),
                new RouteSample("GET", "/api/settlement/routing/mapping"),
                new RouteSample("POST", "/api/settlement/routing/mapping"),
                new RouteSample("PUT", "/api/settlement/routing/mapping/7"),
                new RouteSample("DELETE", "/api/settlement/routing/mapping/7"),
                new RouteSample("PATCH", "/api/settlement/routing/mapping/7/status")
        );

        List<RouteSample> missing = samples.stream()
                .filter(sample -> resources.stream().noneMatch(resource -> resource.matches(sample)))
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void initialResourcesShouldMatchFrontendAdminApiRoutes() throws IOException {
        List<ApiResource> resources = loadInitialApiResources();

        List<RouteSample> samples = List.of(
                new RouteSample("GET", "/api/system/info"),
                new RouteSample("GET", "/api/admin/app-registry"),
                new RouteSample("POST", "/api/admin/app-registry"),
                new RouteSample("GET", "/api/admin/app-registry/7"),
                new RouteSample("PUT", "/api/admin/app-registry/7"),
                new RouteSample("POST", "/api/admin/app-registry/7/rotate-secret"),
                new RouteSample("GET", "/api/admin/integration-configs"),
                new RouteSample("GET", "/api/admin/integration-configs/wechat"),
                new RouteSample("PUT", "/api/admin/integration-configs/wechat"),
                new RouteSample("DELETE", "/api/admin/integration-configs/wechat"),
                new RouteSample("POST", "/api/admin/integration-configs/wechat/enable"),
                new RouteSample("POST", "/api/admin/integration-configs/wechat/test-connection"),
                new RouteSample("POST", "/api/admin/integration-configs/alipay/cert-upload"),
                new RouteSample("POST", "/api/admin/payment/batch"),
                new RouteSample("POST", "/api/admin/payment/batch/7/cancel"),
                new RouteSample("GET", "/api/admin/payment/batch/stats"),
                new RouteSample("GET", "/api/admin/payment/batch/failures"),
                new RouteSample("POST", "/api/admin/payment/batch/failures/7/retry"),
                new RouteSample("GET", "/api/admin/audit-logs/user/alice/recent"),
                new RouteSample("GET", "/api/admin/audit-logs/time-range"),
                new RouteSample("GET", "/api/admin/audit-logs/stats/today-login"),
                new RouteSample("GET", "/api/admin/audit-logs/stats/summary"),
                new RouteSample("GET", "/api/admin/audit-logs/stats/operations"),
                new RouteSample("GET", "/api/admin/audit-logs/security/login-failures"),
                new RouteSample("DELETE", "/api/admin/audit-logs/security/login-failures/alice"),
                new RouteSample("GET", "/api/admin/audit-logs/metrics"),
                new RouteSample("GET", "/api/admin/user-bindings"),
                new RouteSample("GET", "/api/admin/users/1001/platform-binding"),
                new RouteSample("PUT", "/api/admin/users/1001/platform-binding"),
                new RouteSample("DELETE", "/api/admin/users/1001/platform-binding"),
                new RouteSample("PUT", "/api/admin/users/1001/bind-employee/88"),
                new RouteSample("POST", "/api/admin/users/provision-from-employees"),
                new RouteSample("PATCH", "/api/admin/employees/88/offline"),
                new RouteSample("PUT", "/api/admin/employees/88/manager"),
                new RouteSample("GET", "/api/admin/resources/v2/export"),
                new RouteSample("POST", "/api/admin/resources/v2/import"),
                new RouteSample("GET", "/api/admin/roles/7"),
                new RouteSample("GET", "/api/admin/roles/enabled"),
                new RouteSample("PUT", "/api/admin/roles/7/disable"),
                new RouteSample("PUT", "/api/admin/roles/7/enable"),
                new RouteSample("POST", "/api/admin/roles/7/copy"),
                new RouteSample("GET", "/api/admin/roles/7/permissions"),
                new RouteSample("PUT", "/api/admin/roles/7/permissions"),
                new RouteSample("DELETE", "/api/admin/roles/7/permissions"),
                new RouteSample("GET", "/api/admin/roles/7/resources"),
                new RouteSample("PUT", "/api/admin/roles/7/resources"),
                new RouteSample("DELETE", "/api/admin/roles/7/resources"),
                new RouteSample("GET", "/api/admin/users/1001/roles"),
                new RouteSample("GET", "/api/admin/users/search"),
                new RouteSample("GET", "/api/admin/users/1001/resources"),
                new RouteSample("PUT", "/api/admin/users/1001/resources"),
                new RouteSample("GET", "/api/admin/users/1001/aggregate-resources"),
                new RouteSample("GET", "/api/admin/users/1001/effective-resources"),
                new RouteSample("GET", "/api/admin/users/1001/effective-resource-details")
        );

        List<RouteSample> missing = samples.stream()
                .filter(sample -> resources.stream().noneMatch(resource -> resource.matches(sample)))
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void initialResourcePropsJsonShouldNotContainDuplicateFields() throws IOException {
        String json = Files.readString(Path.of("src/main/resources/sql/initial_resources.json"));
        List<Map<String, Object>> rows = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});

        List<String> duplicateProps = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String propsJson = Objects.toString(row.get("propsJson"), null);
            if (!StringUtils.hasText(propsJson)) {
                continue;
            }
            try {
                STRICT_OBJECT_MAPPER.readTree(propsJson);
            } catch (Exception ex) {
                duplicateProps.add(Objects.toString(row.get("code"), "<unknown>"));
            }
        }

        assertThat(duplicateProps).isEmpty();
    }

    @Test
    void initialOrgSyncManagerResourcesShouldOnlyExposeLocalDepartmentTree() throws IOException {
        String json = Files.readString(Path.of("src/main/resources/sql/initial_resources.json"));
        List<Map<String, Object>> rows = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});

        List<String> managerVisibleOrgSyncApis = rows.stream()
                .filter(row -> "API".equals(row.get("type")))
                .filter(row -> Objects.toString(row.get("code"), "").startsWith("api.system.org."))
                .filter(row -> propsJsonRolesContain(Objects.toString(row.get("propsJson"), null), "MANAGER"))
                .map(row -> Objects.toString(row.get("code"), null))
                .toList();

        assertThat(managerVisibleOrgSyncApis).containsExactly("api.system.org.department-tree");
    }

    @Test
    void initialPayrollManagerApiResourcesShouldOnlyExposeManagerScopedReview() throws IOException {
        String json = Files.readString(Path.of("src/main/resources/sql/initial_resources.json"));
        List<Map<String, Object>> rows = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});

        List<String> managerVisiblePayrollApis = rows.stream()
                .filter(row -> "API".equals(row.get("type")))
                .filter(row -> Objects.toString(row.get("code"), "").startsWith("api.payroll."))
                .filter(row -> propsJsonRolesContain(Objects.toString(row.get("propsJson"), null), "MANAGER"))
                .map(row -> Objects.toString(row.get("code"), null))
                .toList();

        assertThat(managerVisiblePayrollApis).containsExactly(
                "api.payroll.confirmations.pending",
                "api.payroll.batches.manager-review"
        );
    }

    @Test
    void initialPayrollConfirmationResourceRolesShouldMatchControllerBoundaries() throws IOException {
        String json = Files.readString(Path.of("src/main/resources/sql/initial_resources.json"));
        List<Map<String, Object>> rows = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});

        assertThat(rolesOf(rows, "api.payroll.confirmations.pending"))
                .containsExactly("ADMIN", "FINANCE", "HR", "MANAGER", "EMPLOYEE");
        assertThat(rolesOf(rows, "api.payroll.confirmations.summary"))
                .containsExactly("ADMIN", "FINANCE", "HR");
        assertThat(rolesOf(rows, "api.payroll.confirmations.assign"))
                .containsExactly("ADMIN", "FINANCE");
    }

    @Test
    void initialApprovalWorkflowResourcesShouldRelyOnObjectLevelControllerChecks() throws IOException {
        String json = Files.readString(Path.of("src/main/resources/sql/initial_resources.json"));
        List<Map<String, Object>> rows = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});

        assertThat(rolesOf(rows, "api.approval.workflow.list"))
                .isEmpty();
        assertThat(rolesOf(rows, "api.approval.workflow.pending"))
                .isEmpty();
        assertThat(rolesOf(rows, "api.approval.workflow.my"))
                .isEmpty();
        assertThat(rolesOf(rows, "api.approval.workflow.detail"))
                .isEmpty();
        assertThat(rolesOf(rows, "api.approval.workflow.steps"))
                .isEmpty();
        assertThat(rolesOf(rows, "api.approval.workflow.approve"))
                .isEmpty();
        assertThat(rolesOf(rows, "api.approval.workflow.reject"))
                .isEmpty();
        assertThat(rolesOf(rows, "api.approval.workflow.cancel"))
                .isEmpty();
        assertThat(rolesOf(rows, "menu.approval"))
                .isEmpty();
    }

    @Test
    void initialAdminUserResourceUpdateShouldAllowManagersToSubmitApprovalRequests() throws IOException {
        String json = Files.readString(Path.of("src/main/resources/sql/initial_resources.json"));
        List<Map<String, Object>> rows = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});

        assertThat(rolesOf(rows, "api.admin.user.resources.read"))
                .containsExactly("ADMIN");
        assertThat(rolesOf(rows, "api.admin.user.resources.update"))
                .containsExactly("ADMIN", "MANAGER");
        assertThat(rolesOf(rows, "api.admin.user.aggregate-resources"))
                .containsExactly("ADMIN");
    }

    private static List<ApiResource> loadInitialApiResources() throws IOException {
        String json = Files.readString(Path.of("src/main/resources/sql/initial_resources.json"));
        List<Map<String, Object>> rows = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        return rows.stream()
                .filter(row -> "API".equals(row.get("type")))
                .map(row -> new ApiResource(
                        Objects.toString(row.get("code"), null),
                        Objects.toString(row.get("path"), null),
                        extractMethod(Objects.toString(row.get("propsJson"), null))
                ))
                .toList();
    }

    private static String extractMethod(String propsJson) {
        if (!StringUtils.hasText(propsJson)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(propsJson).path("method").asText(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean propsJsonRolesContain(String propsJson, String role) {
        if (!StringUtils.hasText(propsJson)) {
            return false;
        }
        try {
            for (var roleNode : OBJECT_MAPPER.readTree(propsJson).path("roles")) {
                if (role.equalsIgnoreCase(roleNode.asText())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private static List<String> rolesOf(List<Map<String, Object>> rows, String code) {
        return rows.stream()
                .filter(row -> code.equals(row.get("code")))
                .findFirst()
                .map(row -> parseRoles(Objects.toString(row.get("propsJson"), null)))
                .orElse(List.of());
    }

    private static List<String> parseRoles(String propsJson) {
        if (!StringUtils.hasText(propsJson)) {
            return List.of();
        }
        try {
            List<String> roles = new ArrayList<>();
            for (var roleNode : OBJECT_MAPPER.readTree(propsJson).path("roles")) {
                roles.add(roleNode.asText());
            }
            return roles;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private record RouteSample(String method, String path) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private record ApiResource(String code, String path, String method) {
        boolean matches(RouteSample sample) {
            return StringUtils.hasText(path)
                    && (method == null || method.equalsIgnoreCase(sample.method()))
                    && PATH_MATCHER.match(path, sample.path());
        }
    }
}
