package com.yiyundao.compensation.interfaces.adapter.impl;

import com.yiyundao.compensation.interfaces.adapter.OrganizationAdapter;
import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yiyundao.compensation.interfaces.dto.config.FeishuConfigDto;
import com.yiyundao.compensation.service.PlatformTokenCacheService;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
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
    private final IntegrationConfigService integrationConfigService;
    private final PlatformTokenCacheService platformTokenCacheService;
    private final SysConfigService sysConfigService;

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
            List<FeishuDepartment> departments = getAllDepartments(tenantToken);
            int total = 0, created = 0, updated = 0;
            List<String> errors = new ArrayList<>();

            // 部门名称映射
            java.util.Map<String, String> deptNameMap = new java.util.HashMap<>();
            for (FeishuDepartment d : departments) {
                deptNameMap.put(d.getDepartment_id(), d.getName());
            }

            for (FeishuDepartment dept : departments) {
                String pageToken = null;
                boolean hasMore = true;
                while (hasMore) {
                    FeishuUserListResponse page = getDepartmentUsers(tenantToken, dept.getDepartment_id(), pageToken);
                    if (page == null || page.getData() == null) break;
                    if (page.getData().getItems() != null) {
                        total += page.getData().getItems().size();
                        for (FeishuUser u : page.getData().getItems()) {
                            try {
                                Employee existing = employeeService.getByPlatformUserId(u.getUser_id(), PLATFORM_TYPE);
                                Employee candidate = convertToEmployee(u, deptNameMap);
                                if (existing == null) {
                                    if (candidate.getEmployeeId() != null) {
                                        Employee byEmpId = employeeService.getByEmployeeId(candidate.getEmployeeId());
                                        if (byEmpId != null) {
                                            employeeService.updateEmployee(byEmpId.getId(), candidate);
                                            updated++;
                                            continue;
                                        }
                                    }
                                    employeeService.createEmployee(candidate);
                                    created++;
                                } else {
                                    employeeService.updateEmployee(existing.getId(), candidate);
                                    updated++;
                                }
                            } catch (Exception ex) {
                                errors.add("同步飞书用户失败: " + u.getName() + ", err=" + ex.getMessage());
                                log.error("同步飞书员工失败: {}", u.getName(), ex);
                            }
                        }
                    }
                    hasMore = page.getData().getHas_more();
                    pageToken = page.getData().getPage_token();
                }
            }

            OrganizationSyncResult result = OrganizationSyncResult.success(PLATFORM_TYPE, total, created, updated);
            if (!errors.isEmpty()) result.setErrors(errors);
            return result;
        } catch (Exception e) {
            log.error("飞书组织架构同步异常", e);
            return OrganizationSyncResult.failure(PLATFORM_TYPE, "同步异常: " + e.getMessage(), null);
        }
    }

    @Override
    public Employee getUserInfo(String platformUserId) {
        try {
            String tenantToken = getTenantAccessToken();
            if (tenantToken == null) return null;
            String url = API_BASE_URL + "/contact/v3/users/" + platformUserId + "?user_id_type=user_id";
            FeishuUserDetailResponse resp = webClient.get()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(tenantToken))
                    .retrieve()
                    .bodyToMono(FeishuUserDetailResponse.class)
                    .block();
            if (resp != null && resp.getCode() == 0 && resp.getData() != null) {
                FeishuUser u = resp.getData().getUser();
                return convertToEmployee(u, null);
            }
        } catch (Exception e) {
            log.error("获取飞书用户信息失败: {}", platformUserId, e);
        }
        return null;
    }

    @Override
    public boolean isManager(String platformUserId) {
        // TODO: 管理员判断
        return true;
    }

    @Override
    public List<Employee> getDepartmentEmployees(String departmentId) {
        List<Employee> list = new ArrayList<>();
        try {
            String tenantToken = getTenantAccessToken();
            if (tenantToken == null) return list;
            String pageToken = null;
            boolean hasMore = true;
            while (hasMore) {
                FeishuUserListResponse page = getDepartmentUsers(tenantToken, departmentId, pageToken);
                if (page == null || page.getData() == null) break;
                if (page.getData().getItems() != null) {
                    for (FeishuUser u : page.getData().getItems()) {
                        list.add(convertToEmployee(u, null));
                    }
                }
                hasMore = page.getData().getHas_more();
                pageToken = page.getData().getPage_token();
            }
        } catch (Exception e) {
            log.error("获取飞书部门员工失败: {}", departmentId, e);
        }
        return list;
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
            FeishuConfigDto cfg = integrationConfigService.getFeishuConfig();
            String id = (cfg != null && cfg.getAppId() != null && !cfg.getAppId().isBlank()) ? cfg.getAppId() : appId;
            String secret = (cfg != null && cfg.getAppSecret() != null && !cfg.getAppSecret().isBlank()) ? cfg.getAppSecret() : appSecret;
            // Treat placeholders as not configured to avoid hitting external API with invalid params
            boolean placeholder = (id != null && id.toLowerCase().startsWith("your_")) || (secret != null && secret.toLowerCase().startsWith("your_"));
            if (placeholder || id == null || id.isBlank() || secret == null || secret.isBlank()) {
                log.warn("未配置 feishu.app-id/app-secret，跳过获取token");
                return null;
            }
            String url = API_BASE_URL + "/auth/v3/tenant_access_token/internal";
            // cache first
            String cached = platformTokenCacheService.getToken(PLATFORM_TYPE);
            if (cached != null && !cached.isBlank()) return cached;
            FeishuTokenRequest req = new FeishuTokenRequest(id, secret);
            FeishuTokenResponse resp = webClient.post().uri(url).bodyValue(req).retrieve().bodyToMono(FeishuTokenResponse.class).block();
            if (resp != null && resp.getCode() == 0) {
                String token = resp.getTenant_access_token();
                int buffer = sysConfigService.getInt("oauth.token.ttl.buffer.seconds", 300);
                long ttl = resp.getExpire() != null ? Math.max(60, resp.getExpire() - Math.max(0, buffer)) : 6900;
                platformTokenCacheService.setToken(PLATFORM_TYPE, token, ttl);
                return token;
            }
            log.error("获取飞书访问令牌失败: {}", resp != null ? resp.getMsg() : "null response");
            return null;
        } catch (Exception e) {
            log.error("获取飞书访问令牌异常", e);
            return null;
        }
    }

    private List<FeishuDepartment> getAllDepartments(String tenantToken) {
        List<FeishuDepartment> list = new ArrayList<>();
        try {
            String pageToken = null;
            boolean hasMore = true;
            while (hasMore) {
                String url = API_BASE_URL + "/contact/v3/departments?fetch_child=true&page_size=50" +
                        (pageToken != null ? "&page_token=" + pageToken : "");
                FeishuDepartmentListResponse resp = webClient.get()
                        .uri(url)
                        .headers(h -> h.setBearerAuth(tenantToken))
                        .retrieve()
                        .bodyToMono(FeishuDepartmentListResponse.class)
                        .block();
                if (resp == null || resp.getCode() != 0 || resp.getData() == null) break;
                if (resp.getData().getItems() != null) list.addAll(resp.getData().getItems());
                hasMore = resp.getData().getHas_more();
                pageToken = resp.getData().getPage_token();
            }
        } catch (Exception e) {
            log.error("获取飞书部门异常", e);
        }
        return list;
    }

    private FeishuUserListResponse getDepartmentUsers(String tenantToken, String departmentId, String pageToken) {
        try {
            String url = API_BASE_URL + "/contact/v3/users?department_id=" + departmentId +
                    "&page_size=50&user_id_type=user_id&department_id_type=department_id" +
                    (pageToken != null ? "&page_token=" + pageToken : "");
            return webClient.get()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(tenantToken))
                    .retrieve()
                    .bodyToMono(FeishuUserListResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("分页获取飞书用户失败: deptId={}", departmentId, e);
            return null;
        }
    }

    private Employee convertToEmployee(FeishuUser u, java.util.Map<String, String> deptNameMap) {
        Employee e = new Employee();
        String empId = (u.getEmployee_no() != null && !u.getEmployee_no().isBlank()) ? u.getEmployee_no() : u.getUser_id();
        e.setEmployeeId(empId);
        e.setName(u.getName());
        e.setPhone(u.getMobile());
        String email = u.getEnterprise_email() != null ? u.getEnterprise_email() : u.getEmail();
        e.setEmail(email);
        e.setPlatformUserId(u.getUser_id());
        e.setPlatformType(PLATFORM_TYPE);
        if (deptNameMap != null && u.getDepartment_ids() != null && !u.getDepartment_ids().isEmpty()) {
            e.setDepartment(deptNameMap.getOrDefault(u.getDepartment_ids().get(0), null));
        }
        e.setPosition(u.getJob_title());
        boolean active = u.getStatus() == null || (!Boolean.TRUE.equals(u.getStatus().getIs_resigned()));
        e.setStatus(active ? "active" : "inactive");
        e.setOffline(false);
        return e;
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
        private Integer expire;
        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public String getMsg() { return msg; }
        public void setMsg(String msg) { this.msg = msg; }
        public String getTenant_access_token() { return tenant_access_token; }
        public void setTenant_access_token(String tenant_access_token) { this.tenant_access_token = tenant_access_token; }
        public Integer getExpire() { return expire; }
        public void setExpire(Integer expire) { this.expire = expire; }
    }

    private static class FeishuDepartmentListResponse {
        private int code;
        private String msg;
        private FeishuDepartmentListData data;
        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public String getMsg() { return msg; }
        public void setMsg(String msg) { this.msg = msg; }
        public FeishuDepartmentListData getData() { return data; }
        public void setData(FeishuDepartmentListData data) { this.data = data; }
    }

    private static class FeishuDepartmentListData {
        private java.util.List<FeishuDepartment> items;
        private boolean has_more;
        private String page_token;
        public java.util.List<FeishuDepartment> getItems() { return items; }
        public void setItems(java.util.List<FeishuDepartment> items) { this.items = items; }
        public boolean getHas_more() { return has_more; }
        public void setHas_more(boolean has_more) { this.has_more = has_more; }
        public String getPage_token() { return page_token; }
        public void setPage_token(String page_token) { this.page_token = page_token; }
    }

    private static class FeishuDepartment {
        private String department_id;
        private String name;
        private String parent_department_id;
        public String getDepartment_id() { return department_id; }
        public void setDepartment_id(String department_id) { this.department_id = department_id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getParent_department_id() { return parent_department_id; }
        public void setParent_department_id(String parent_department_id) { this.parent_department_id = parent_department_id; }
    }

    private static class FeishuUserListResponse {
        private int code;
        private String msg;
        private FeishuUserListData data;
        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public String getMsg() { return msg; }
        public void setMsg(String msg) { this.msg = msg; }
        public FeishuUserListData getData() { return data; }
        public void setData(FeishuUserListData data) { this.data = data; }
    }

    private static class FeishuUserListData {
        private java.util.List<FeishuUser> items;
        private boolean has_more;
        private String page_token;
        public java.util.List<FeishuUser> getItems() { return items; }
        public void setItems(java.util.List<FeishuUser> items) { this.items = items; }
        public boolean getHas_more() { return has_more; }
        public void setHas_more(boolean has_more) { this.has_more = has_more; }
        public String getPage_token() { return page_token; }
        public void setPage_token(String page_token) { this.page_token = page_token; }
    }

    private static class FeishuUserDetailResponse {
        private int code;
        private String msg;
        private FeishuUserDetailData data;
        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public String getMsg() { return msg; }
        public void setMsg(String msg) { this.msg = msg; }
        public FeishuUserDetailData getData() { return data; }
        public void setData(FeishuUserDetailData data) { this.data = data; }
    }

    private static class FeishuUserDetailData {
        private FeishuUser user;
        public FeishuUser getUser() { return user; }
        public void setUser(FeishuUser user) { this.user = user; }
    }

    private static class FeishuUser {
        private String user_id;
        private String name;
        private String email;
        private String enterprise_email;
        private String mobile;
        private java.util.List<String> department_ids;
        private String job_title;
        private FeishuUserStatus status;
        private String employee_no;
        public String getUser_id() { return user_id; }
        public void setUser_id(String user_id) { this.user_id = user_id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getEnterprise_email() { return enterprise_email; }
        public void setEnterprise_email(String enterprise_email) { this.enterprise_email = enterprise_email; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public java.util.List<String> getDepartment_ids() { return department_ids; }
        public void setDepartment_ids(java.util.List<String> department_ids) { this.department_ids = department_ids; }
        public String getJob_title() { return job_title; }
        public void setJob_title(String job_title) { this.job_title = job_title; }
        public FeishuUserStatus getStatus() { return status; }
        public void setStatus(FeishuUserStatus status) { this.status = status; }
        public String getEmployee_no() { return employee_no; }
        public void setEmployee_no(String employee_no) { this.employee_no = employee_no; }
    }

    private static class FeishuUserStatus {
        private Boolean is_frozen;
        private Boolean is_resigned;
        private Boolean is_activated;
        private Boolean is_unjoin;
        public Boolean getIs_frozen() { return is_frozen; }
        public void setIs_frozen(Boolean is_frozen) { this.is_frozen = is_frozen; }
        public Boolean getIs_resigned() { return is_resigned; }
        public void setIs_resigned(Boolean is_resigned) { this.is_resigned = is_resigned; }
        public Boolean getIs_activated() { return is_activated; }
        public void setIs_activated(Boolean is_activated) { this.is_activated = is_activated; }
        public Boolean getIs_unjoin() { return is_unjoin; }
        public void setIs_unjoin(Boolean is_unjoin) { this.is_unjoin = is_unjoin; }
    }
}
