package com.yiyundao.compensation.interfaces.adapter.impl;

import com.yiyundao.compensation.interfaces.adapter.OrganizationAdapter;
import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuOrganizationAdapter implements OrganizationAdapter {

    private final EmployeeService employeeService;
    private final WebClient webClient;

    @Value("${feishu.app-id:}")
    private String appId;

    @Value("${feishu.app-secret:}")
    private String appSecret;

    private static final String PLATFORM_TYPE = "feishu";
    private static final String API_BASE_URL = "https://open.feishu.cn/open-apis";

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    public OrganizationSyncResult syncOrganization() {
        log.info("开始飞书组织架构同步");
        try {
            String tenantToken = getTenantAccessToken();
            if (tenantToken == null) {
                return OrganizationSyncResult.failure(PLATFORM_TYPE, "获取访问令牌失败", null);
            }
            // TODO: 获取部门与用户并同步
            int total = 0, created = 0, updated = 0;
            return OrganizationSyncResult.success(PLATFORM_TYPE, total, created, updated);
        } catch (Exception e) {
            log.error("飞书组织架构同步异常", e);
            return OrganizationSyncResult.failure(PLATFORM_TYPE, "同步异常: " + e.getMessage(), null);
        }
    }

    @Override
    public Employee getUserInfo(String platformUserId) {
        // TODO: 飞书用户详情
        return null;
    }

    @Override
    public boolean isManager(String platformUserId) {
        // TODO: 管理员判断
        return true;
    }

    @Override
    public List<Employee> getDepartmentEmployees(String departmentId) {
        return new ArrayList<>();
    }

    @Override
    public void sendApprovalNotification(String platformUserId, String message) {
        try {
            String tenantToken = getTenantAccessToken();
            if (tenantToken == null) return;
            // TODO: 使用消息接口发送
            log.info("[Feishu] 通知: userId={}, message={}", platformUserId, message);
        } catch (Exception e) {
            log.error("发送飞书通知失败", e);
        }
    }

    @Override
    public boolean checkConnection() {
        try {
            return getTenantAccessToken() != null;
        } catch (Exception e) {
            log.error("检查飞书连接失败", e);
            return false;
        }
    }

    private String getTenantAccessToken() {
        try {
            if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
                log.warn("未配置 feishu.app-id/app-secret，跳过获取token");
                return null;
            }
            String url = API_BASE_URL + "/auth/v3/tenant_access_token/internal";
            FeishuTokenRequest req = new FeishuTokenRequest(appId, appSecret);
            FeishuTokenResponse resp = webClient.post().uri(url).bodyValue(req).retrieve().bodyToMono(FeishuTokenResponse.class).block();
            if (resp != null && resp.getCode() == 0) {
                return resp.getTenant_access_token();
            }
            log.error("获取飞书访问令牌失败: {}", resp != null ? resp.getMsg() : "null response");
            return null;
        } catch (Exception e) {
            log.error("获取飞书访问令牌异常", e);
            return null;
        }
    }

    private static class FeishuTokenRequest {
        private String app_id;
        private String app_secret;
        public FeishuTokenRequest(String appId, String appSecret) { this.app_id = appId; this.app_secret = appSecret; }
        public String getApp_id() { return app_id; }
        public String getApp_secret() { return app_secret; }
    }

    private static class FeishuTokenResponse {
        private int code;
        private String msg;
        private String tenant_access_token;
        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public String getMsg() { return msg; }
        public void setMsg(String msg) { this.msg = msg; }
        public String getTenant_access_token() { return tenant_access_token; }
        public void setTenant_access_token(String tenant_access_token) { this.tenant_access_token = tenant_access_token; }
    }
}

