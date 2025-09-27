package com.yiyundao.compensation.interfaces.controller.system;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.service.OrganizationSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/org")
@RequiredArgsConstructor
public class OrganizationSyncController {

    private final OrganizationSyncService organizationSyncService;

    // 触发单平台或全平台同步，platform=wechat|dingtalk|feishu|all
    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER') or hasAuthority('org:sync')")
    public ApiResponse<?> sync(@RequestParam(defaultValue = "all") String platform) {
        if ("all".equalsIgnoreCase(platform)) {
            List<OrganizationSyncResult> results = organizationSyncService.syncAllPlatforms();
            return ApiResponse.success(results);
        }
        return ApiResponse.success(organizationSyncService.syncPlatform(platform));
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
    public ApiResponse<Boolean> check(@RequestParam String platform) {
        return ApiResponse.success(organizationSyncService.checkPlatformConnection(platform));
    }
}

