package com.yiyundao.compensation.interfaces.controller.system;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import com.yiyundao.compensation.service.OrganizationSyncService;
import com.yiyundao.compensation.modules.system.service.OrgSyncTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/org")
@RequiredArgsConstructor
public class OrganizationSyncController {

    private final OrganizationSyncService organizationSyncService;
    private final AuditLogService auditLogService;
    private final OrgSyncTaskService orgSyncTaskService;

    // 触发单平台或全平台同步，platform=wechat|dingtalk|feishu|all
    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER') or hasAuthority('org:sync')")
    public ApiResponse<?> sync(@RequestParam(defaultValue = "all") String platform, HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        if ("all".equalsIgnoreCase(platform)) {
            List<OrganizationSyncResult> results = organizationSyncService.syncAllPlatforms();
            auditLogService.record("ORG_SYNC_ALL", request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), request.getHeader("User-Agent"),
                    "ORG", "all", request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                    null, "OK", null, System.currentTimeMillis() - begin);
            return ApiResponse.success(results);
        }
        OrganizationSyncResult result = organizationSyncService.syncPlatform(platform);
        auditLogService.record("ORG_SYNC", request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), request.getHeader("User-Agent"),
                "ORG", platform, request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                null, result.isSuccess() ? "OK" : "FAILED", null, System.currentTimeMillis() - begin);
        return ApiResponse.success(result);
    }

    // 查看支持的平台列表
    @GetMapping("/platforms")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<String>> platforms() {
        return ApiResponse.success(organizationSyncService.getSupportedPlatforms());
    }

    // 检查连接
    @GetMapping("/check")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER') or hasAuthority('org:read')")
    public ApiResponse<Boolean> check(@RequestParam String platform, HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        boolean ok = organizationSyncService.checkPlatformConnection(platform);
        auditLogService.record("ORG_CHECK", request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), request.getHeader("User-Agent"),
                "ORG", platform, request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                null, ok ? "OK" : "FAILED", null, System.currentTimeMillis() - begin);
        return ApiResponse.success(ok);
    }

    // 异步任务：创建并返回任务ID
    @PostMapping("/sync-async")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER') or hasAuthority('org:sync')")
    public ApiResponse<String> syncAsync(@RequestParam(defaultValue = "all") String platform, HttpServletRequest request) {
        String taskId = orgSyncTaskService.start(platform);
        auditLogService.record("ORG_SYNC_ASYNC", request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), request.getHeader("User-Agent"),
                "ORG", platform, request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                null, taskId, null, 0L);
        return ApiResponse.success(taskId);
    }

    // 异步任务查询
    @GetMapping("/sync-task/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER') or hasAuthority('org:read')")
    public ApiResponse<OrgSyncTaskService.TaskInfo> task(@PathVariable String id) {
        return ApiResponse.success(orgSyncTaskService.get(id));
    }
}
