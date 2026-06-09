package com.yiyundao.compensation.interfaces.controller;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.modules.user.service.LegacyPlatformFieldPolicy;
import com.yiyundao.compensation.security.SecurityAnnotations;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    private final LegacyPlatformFieldPolicy legacyPlatformFieldPolicy;

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("status", "UP");
        healthInfo.put("timestamp", LocalDateTime.now());
        healthInfo.put("service", "Compensation Assistant System");
        healthInfo.put("version", "1.0.0");

        return ApiResponse.<Map<String, Object>>success("系统运行正常", healthInfo);
    }

    @GetMapping("/info")
    @SecurityAnnotations.IsAdmin
    public ApiResponse<Map<String, Object>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "薪酬助手系统");
        info.put("description", "支持支付宝批量转账、多平台组织同步、架构外员工管理");
        info.put("features", new String[]{
            "支付宝批量转账",
            "企业微信/钉钉/飞书集成",
            "架构外员工管理",
            "动态审批流程",
            "SM4/AES双重加密"
        });
        info.put("legacyPlatformFieldMode", legacyPlatformFieldPolicy.getMode().name().toLowerCase());

        return ApiResponse.success(info);
    }
}
