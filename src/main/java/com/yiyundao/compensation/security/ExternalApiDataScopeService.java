package com.yiyundao.compensation.security;

import com.yiyundao.compensation.modules.app.entity.AppDataGrant;
import com.yiyundao.compensation.modules.app.service.AppDataGrantService;
import com.yiyundao.compensation.modules.employee.service.EmployeeDepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves object-level authorization for external applications. A scope without at least
 * one active data grant is deliberately denied.
 */
@Service
@RequiredArgsConstructor
public class ExternalApiDataScopeService {

    private final AppDataGrantService appDataGrantService;
    private final EmployeeDepartmentService employeeDepartmentService;

    @Value("${external-api.tenant-id:default}")
    private String currentTenantId = "default";

    public DataScope resolve(ExternalApiContext.ExternalApiClient client) {
        if (client == null || client.getAppId() == null) {
            throw new AccessDeniedException("外部应用缺少数据授权上下文");
        }
        List<AppDataGrant> grants = appDataGrantService.listActiveByAppId(client.getAppId());
        if (grants.isEmpty()) {
            throw new AccessDeniedException("外部应用未配置数据范围授权");
        }

        boolean allTenant = false;
        Set<Long> employeeIds = new LinkedHashSet<>();
        Set<Long> batchIds = new LinkedHashSet<>();
        for (AppDataGrant grant : grants) {
            if (AppDataGrantService.TENANT.equals(grant.getScopeType())) {
                if (currentTenantId != null && currentTenantId.equals(grant.getScopeValue())) {
                    allTenant = true;
                }
                continue;
            }
            Long scopeId = parseId(grant.getScopeValue());
            if (scopeId == null) {
                continue;
            }
            switch (grant.getScopeType()) {
                case AppDataGrantService.EMPLOYEE -> employeeIds.add(scopeId);
                case AppDataGrantService.PAYROLL_BATCH -> batchIds.add(scopeId);
                case AppDataGrantService.DEPARTMENT -> employeeIds.addAll(
                        employeeDepartmentService.findEmployeeIdsByLocalDepartmentId(scopeId));
                default -> {
                    // Validation prevents unknown types from being stored.
                }
            }
        }
        if (!allTenant && employeeIds.isEmpty() && batchIds.isEmpty()) {
            throw new AccessDeniedException("外部应用数据授权为空");
        }
        return new DataScope(allTenant, Set.copyOf(employeeIds), Set.copyOf(batchIds));
    }

    private Long parseId(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record DataScope(boolean allTenant, Set<Long> employeeIds, Set<Long> batchIds) {
        public boolean allowsBatchId(Long batchId) {
            return allTenant || (batchId != null && batchIds.contains(batchId));
        }

        public boolean allowsEmployeeId(Long employeeId) {
            return allTenant || (employeeId != null && employeeIds.contains(employeeId));
        }

        public boolean filtersByEmployee() {
            return !allTenant && !employeeIds.isEmpty();
        }
    }
}
