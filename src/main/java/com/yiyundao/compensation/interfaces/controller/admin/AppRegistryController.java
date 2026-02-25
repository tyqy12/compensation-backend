package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.interfaces.dto.app.AppRegistryCreateRequest;
import com.yiyundao.compensation.interfaces.dto.app.AppRegistryResponse;
import com.yiyundao.compensation.interfaces.dto.app.AppRegistrySecretResponse;
import com.yiyundao.compensation.interfaces.dto.app.AppRegistryUpdateRequest;
import com.yiyundao.compensation.modules.app.entity.AppRegistry;
import com.yiyundao.compensation.modules.app.service.AppRegistryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.yiyundao.compensation.security.SecurityAnnotations;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/app-registry")
@RequiredArgsConstructor
@SecurityAnnotations.IsAdmin
public class AppRegistryController {

    private final AppRegistryService appRegistryService;

    @PostMapping
    public ApiResponse<AppRegistrySecretResponse> create(@Valid @RequestBody AppRegistryCreateRequest request) {
        var cmd = new AppRegistryService.AppRegistryCreateCommand(
                request.getAppName(),
                request.getScopes(),
                request.getIpWhitelist(),
                request.getWebhookUrl(),
                request.getStatus()
        );
        var registered = appRegistryService.register(cmd);
        return ApiResponse.success(toSecretResponse(registered));
    }

    @PutMapping("/{id}")
    public ApiResponse<AppRegistryResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody AppRegistryUpdateRequest request) {
        var cmd = new AppRegistryService.AppRegistryUpdateCommand(
                request.getAppName(),
                request.getScopes(),
                request.getIpWhitelist(),
                request.getWebhookUrl(),
                request.getStatus()
        );
        AppRegistry updated = appRegistryService.updateApp(id, cmd);
        return ApiResponse.success(toResponse(updated));
    }

    @PostMapping("/{id}/rotate-secret")
    public ApiResponse<AppRegistrySecretResponse> rotateSecret(@PathVariable Long id) {
        var result = appRegistryService.rotateSecret(id);
        return ApiResponse.success(toSecretResponse(result));
    }

    @GetMapping
    public ApiResponse<Page<AppRegistryResponse>> list(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String status) {
        Page<AppRegistry> p = new Page<>(page, size);
        LambdaQueryWrapper<AppRegistry> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(AppRegistry::getAppName, keyword)
                    .or()
                    .like(AppRegistry::getClientId, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(AppRegistry::getStatus, status.trim());
        }
        Page<AppRegistry> pageData = appRegistryService.page(p, wrapper);
        Page<AppRegistryResponse> converted = new Page<>(pageData.getCurrent(), pageData.getSize(), pageData.getTotal());
        converted.setRecords(pageData.getRecords().stream().map(this::toResponse).toList());
        return ApiResponse.success(converted);
    }

    @GetMapping("/{id}")
    public ApiResponse<AppRegistryResponse> detail(@PathVariable Long id) {
        AppRegistry app = appRegistryService.getById(id);
        return ApiResponse.success(app == null ? null : toResponse(app));
    }

    private AppRegistrySecretResponse toSecretResponse(AppRegistryService.RegisteredClient registeredClient) {
        AppRegistry app = registeredClient.appRegistry();
        return AppRegistrySecretResponse.builder()
                .id(app.getId())
                .appName(app.getAppName())
                .clientId(app.getClientId())
                .clientSecret(registeredClient.clientSecretPlain())
                .scopes(appRegistryService.resolveScopes(app))
                .ipWhitelist(appRegistryService.resolveIpWhitelist(app))
                .webhookUrl(app.getWebhookUrl())
                .status(app.getStatus())
                .createTime(app.getCreateTime())
                .updateTime(app.getUpdateTime())
                .build();
    }

    private AppRegistryResponse toResponse(AppRegistry app) {
        return AppRegistryResponse.builder()
                .id(app.getId())
                .appName(app.getAppName())
                .clientId(app.getClientId())
                .scopes(appRegistryService.resolveScopes(app))
                .ipWhitelist(appRegistryService.resolveIpWhitelist(app))
                .webhookUrl(app.getWebhookUrl())
                .status(app.getStatus())
                .createTime(app.getCreateTime())
                .updateTime(app.getUpdateTime())
                .build();
    }
}
