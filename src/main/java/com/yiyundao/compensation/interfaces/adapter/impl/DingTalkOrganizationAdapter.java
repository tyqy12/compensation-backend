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
public class DingTalkOrganizationAdapter implements OrganizationAdapter {

    private final EmployeeService employeeService;
    private final WebClient webClient;

    @Value("${dingtalk.app-key:}")
    private String appKey;

    @Value("${dingtalk.app-secret:}")
    private String appSecret;

    private static final String PLATFORM_TYPE = "dingtalk";
    private static final String API_BASE_URL = "https://oapi.dingtalk.com";

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    public OrganizationSyncResult syncOrganization() {
        log.info("开始钉钉组织架构同步");
        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                return OrganizationSyncResult.failure(PLATFORM_TYPE, "获取访问令牌失败", null);
            }

            // TODO: 拉取部门与用户信息并与本地员工对齐
            int total = 0, created = 0, updated = 0;
            return OrganizationSyncResult.success(PLATFORM_TYPE, total, created, updated);
        } catch (Exception e) {
            log.error("钉钉组织架构同步异常", e);
            return OrganizationSyncResult.failure(PLATFORM_TYPE, "同步异常: " + e.getMessage(), null);
        }
    }

    @Override
    public Employee getUserInfo(String platformUserId) {
        // TODO: 调用钉钉获取用户详情
        return null;
    }

    @Override
    public boolean isManager(String platformUserId) {
        // TODO: 判断钉钉管理员
        return true;
    }

    @Override
    public List<Employee> getDepartmentEmployees(String departmentId) {
        return new ArrayList<>();
    }

    @Override
    public void sendApprovalNotification(String platformUserId, String message) {
        try {
            String accessToken = getAccessToken();
            if (accessToken == null) return;
            // TODO: 使用工作通知/机器人发送消息
            log.info("[DingTalk] 通知: userId={}, message={}", platformUserId, message);
        } catch (Exception e) {
            log.error("发送钉钉通知失败", e);
        }
    }

    @Override
    public boolean checkConnection() {
        try {
            return getAccessToken() != null;
        } catch (Exception e) {
            log.error("检查钉钉连接失败", e);
            return false;
        }
    }

    private String getAccessToken() {
        try {
            if (appKey == null || appKey.isBlank() || appSecret == null || appSecret.isBlank()) {
                log.warn("未配置 dingtalk.app-key/app-secret，跳过获取token");
                return null;
            }
            // 接口: https://oapi.dingtalk.com/gettoken?appkey=xxx&appsecret=xxx
            String url = API_BASE_URL + "/gettoken?appkey=" + appKey + "&appsecret=" + appSecret;
            DingTokenResponse resp = webClient.get().uri(url).retrieve().bodyToMono(DingTokenResponse.class).block();
            if (resp != null && resp.getErrcode() == 0) {
                return resp.getAccess_token();
            }
            log.error("获取钉钉访问令牌失败: {}", resp != null ? resp.getErrmsg() : "null response");
            return null;
        } catch (Exception e) {
            log.error("获取钉钉访问令牌异常", e);
            return null;
        }
    }

    private static class DingTokenResponse {
        private int errcode;
        private String errmsg;
        private String access_token;

        public int getErrcode() { return errcode; }
        public void setErrcode(int errcode) { this.errcode = errcode; }
        public String getErrmsg() { return errmsg; }
        public void setErrmsg(String errmsg) { this.errmsg = errmsg; }
        public String getAccess_token() { return access_token; }
        public void setAccess_token(String access_token) { this.access_token = access_token; }
    }
}

