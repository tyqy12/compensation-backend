package com.yiyundao.compensation.interfaces.controller.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto;
import com.yiyundao.compensation.interfaces.dto.config.DingTalkConfigDto;
import com.yiyundao.compensation.interfaces.dto.config.FeishuConfigDto;
import com.yiyundao.compensation.interfaces.dto.config.WechatConfigDto;
import com.yiyundao.compensation.modules.system.entity.IntegrationConfig;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/integration")
@PreAuthorize("hasRole('ADMIN')")
public class IntegrationConfigController {

    private final IntegrationConfigService integrationConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final com.yiyundao.compensation.modules.audit.service.AuditLogService auditLogService;
    private final com.yiyundao.compensation.service.OrganizationSyncService organizationSyncService;

    @GetMapping("/{platformType}")
    public ResponseEntity<?> getConfig(@PathVariable String platformType, jakarta.servlet.http.HttpServletRequest request) {
        IntegrationConfig cfg = integrationConfigService.getRawConfig(platformType);
        if (cfg == null) return ResponseEntity.ok().body(null);
        try {
            switch (platformType) {
                case "wechat":
                    WechatConfigDto wc = integrationConfigService.getWechatConfig();
                    return ResponseEntity.ok(mask(wc));
                case "dingtalk":
                    DingTalkConfigDto dc = integrationConfigService.getDingTalkConfig();
                    return ResponseEntity.ok(mask(dc));
                case "feishu":
                    FeishuConfigDto fc = integrationConfigService.getFeishuConfig();
                    return ResponseEntity.ok(mask(fc));
                case "alipay":
                    AlipayConfigDto ac = integrationConfigService.getAlipayConfig();
                    return ResponseEntity.ok(mask(ac));
                default:
                    return ResponseEntity.badRequest().body("unsupported platform type");
            }
        } catch (Exception e) {
            log.error("读取配置失败", e);
            auditSafe("INTEGRATION_CONFIG_READ", platformType, request, false, e.getMessage());
            return ResponseEntity.internalServerError().body("read config failed");
        }
        finally {
            auditSafe("INTEGRATION_CONFIG_READ", platformType, request, true, null);
        }
    }

    @PutMapping("/{platformType}")
    public ResponseEntity<?> saveConfig(@PathVariable String platformType,
                                        @RequestBody SaveConfigRequest req,
                                        jakarta.servlet.http.HttpServletRequest request) {
        try {
            String json;
            switch (platformType) {
                case "wechat":
                    json = objectMapper.writeValueAsString(req.getWechat());
                    break;
                case "dingtalk":
                    json = objectMapper.writeValueAsString(req.getDingtalk());
                    break;
                case "feishu":
                    json = objectMapper.writeValueAsString(req.getFeishu());
                    break;
                case "alipay":
                    json = objectMapper.writeValueAsString(req.getAlipay());
                    break;
                default:
                    return ResponseEntity.badRequest().body("unsupported platform type");
            }
            integrationConfigService.saveOrUpdate(platformType, json, Boolean.TRUE.equals(req.getEnabled()));
            auditSafe("INTEGRATION_CONFIG_SAVE", platformType, request, true, null);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("保存配置失败", e);
            auditSafe("INTEGRATION_CONFIG_SAVE", platformType, request, false, e.getMessage());
            return ResponseEntity.internalServerError().body("save config failed");
        }
    }

    // 连接性测试（使用当前数据库中的配置）
    @PostMapping("/{platformType}/test-connection")
    public com.yiyundao.compensation.common.response.ApiResponse<Boolean> testConnection(@PathVariable String platformType,
                                                                                         jakarta.servlet.http.HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        boolean ok;
        try {
            ok = organizationSyncService.checkPlatformConnection(platformType);
            auditLogService.record("INTEGRATION_CONFIG_TEST", request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), request.getHeader("User-Agent"),
                    "INTEGRATION", platformType, request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                    null, ok ? "OK" : "FAILED", null, System.currentTimeMillis() - begin);
            return com.yiyundao.compensation.common.response.ApiResponse.success(ok);
        } catch (Exception e) {
            auditLogService.record("INTEGRATION_CONFIG_TEST", request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), request.getHeader("User-Agent"),
                    "INTEGRATION", platformType, request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                    null, "FAILED", e.getMessage(), System.currentTimeMillis() - begin);
            return com.yiyundao.compensation.common.response.ApiResponse.error("connectivity test failed");
        }
    }

    @Data
    public static class SaveConfigRequest {
        private Boolean enabled;
        private WechatConfigDto wechat;
        private DingTalkConfigDto dingtalk;
        private FeishuConfigDto feishu;
        private AlipayConfigDto alipay;
    }

    private void auditSafe(String op, String platform, jakarta.servlet.http.HttpServletRequest req, boolean success, String err) {
        try {
            auditLogService.record(op, req.getMethod(), req.getRequestURI(), req.getRemoteAddr(), req.getHeader("User-Agent"),
                    "INTEGRATION", platform, req.getUserPrincipal() != null ? req.getUserPrincipal().getName() : null,
                    null, success ? "OK" : "FAILED", err, 0L);
        } catch (Exception ignore) {}
    }

    // 脱敏返回，避免在前端泄露敏感数据
    private WechatConfigDto mask(WechatConfigDto dto) {
        if (dto == null) return null;
        WechatConfigDto c = new WechatConfigDto();
        c.setCorpId(maskKeepTail(dto.getCorpId()));
        c.setCorpSecret(maskAll(dto.getCorpSecret()));
        c.setAgentId(dto.getAgentId());
        return c;
    }

    private DingTalkConfigDto mask(DingTalkConfigDto dto) {
        if (dto == null) return null;
        DingTalkConfigDto c = new DingTalkConfigDto();
        c.setAppKey(maskKeepTail(dto.getAppKey()));
        c.setAppSecret(maskAll(dto.getAppSecret()));
        return c;
    }

    private FeishuConfigDto mask(FeishuConfigDto dto) {
        if (dto == null) return null;
        FeishuConfigDto c = new FeishuConfigDto();
        c.setAppId(maskKeepTail(dto.getAppId()));
        c.setAppSecret(maskAll(dto.getAppSecret()));
        return c;
    }

    private AlipayConfigDto mask(AlipayConfigDto dto) {
        if (dto == null) return null;
        AlipayConfigDto c = new AlipayConfigDto();
        c.setAppId(maskKeepTail(dto.getAppId()));
        c.setServerUrl(dto.getServerUrl());
        c.setPrivateKey(maskAll(dto.getPrivateKey()));
        c.setPublicKey(maskKeepTail(dto.getPublicKey()));
        c.setCharset(dto.getCharset());
        c.setSignType(dto.getSignType());
        c.setFormat(dto.getFormat());
        c.setNotifyUrl(dto.getNotifyUrl());
        c.setReturnUrl(dto.getReturnUrl());
        return c;
    }

    private String maskAll(String s) {
        if (s == null || s.isBlank()) return s;
        return "******";
    }

    private String maskKeepTail(String s) {
        if (s == null || s.isBlank()) return s;
        int len = s.length();
        if (len <= 4) return "****";
        return "***" + s.substring(len - 4);
    }
}
