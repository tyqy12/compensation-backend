package com.yiyundao.compensation.interfaces.controller.openapi;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.security.ExternalApiContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class OpenApiPingController {

    private final ExternalApiContext externalApiContext;

    @GetMapping("/ping")
    @PreAuthorize("hasAuthority('ROLE_APP')")
    public ApiResponse<Map<String, Object>> ping() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "ok");
        payload.put("timestamp", System.currentTimeMillis());
        ExternalApiContext.ExternalApiClient client = externalApiContext.current();
        if (client != null) {
            payload.put("clientId", client.getClientId());
            payload.put("appName", client.getAppName());
            payload.put("scopes", client.getScopes());
        }
        return ApiResponse.success(payload);
    }
}

