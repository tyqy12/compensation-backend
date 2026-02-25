package com.yiyundao.compensation.interfaces.adapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.interfaces.adapter.OrganizationAdapter;
import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yiyundao.compensation.interfaces.dto.config.FeishuConfigDto;
import com.yiyundao.compensation.service.PlatformTokenCacheService;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import com.yiyundao.compensation.modules.user.service.UserBindingService;
import com.yiyundao.compensation.modules.org.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuOrganizationAdapter implements OrganizationAdapter {

    private final ObjectProvider<EmployeeService> employeeServiceProvider;
    private final ObjectProvider<UserBindingService> userBindingServiceProvider;
    private final WebClient webClient;
    private final IntegrationConfigService integrationConfigService;
    private final PlatformTokenCacheService platformTokenCacheService;
    private final SysConfigService sysConfigService;
    private final DepartmentService departmentService;
    private final ObjectMapper objectMapper;

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
            if (!integrationConfigService.isPlatformEnabled(PLATFORM_TYPE)) {
                log.warn("飞书未启用或未配置，跳过同步");
                return OrganizationSyncResult.failure(PLATFORM_TYPE, "未启用或未配置，跳过", null);
            }
            String tenantToken = getTenantAccessToken();
            if (tenantToken == null) {
                return OrganizationSyncResult.failure(PLATFORM_TYPE, "获取访问令牌失败", null);
            }
            List<FeishuDepartment> departments = getAllDepartments(tenantToken);
            for (FeishuDepartment d : departments) {
                String parentPid = (d.getParent_department_id() == null || d.getParent_department_id().isBlank()) ? null : d.getParent_department_id();
                departmentService.upsert(PLATFORM_TYPE, d.getDepartment_id(), d.getName(), parentPid, null);
            }
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
                                Employee existing = employeeServiceProvider.getObject().getByPlatformUserId(u.getUser_id(), PLATFORM_TYPE);
                                Employee candidate = convertToEmployee(u, deptNameMap);
                                if (existing == null) {
                                    if (candidate.getEmployeeId() != null) {
                                        Employee byEmpId = employeeServiceProvider.getObject().getByEmployeeId(candidate.getEmployeeId());
                                        if (byEmpId != null) {
                                            employeeServiceProvider.getObject().updateEmployee(byEmpId.getId(), candidate);
                                            updated++;
                                            continue;
                                        }
                                    }
                                    employeeServiceProvider.getObject().createEmployee(candidate);
                                    userBindingServiceProvider.getObject().ensureUserForEmployee(candidate);
                                    created++;
                                } else {
                                    employeeServiceProvider.getObject().updateEmployee(existing.getId(), candidate);
                                    userBindingServiceProvider.getObject().ensureUserForEmployee(candidate);
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
    public java.util.List<Employee> fetchAllEmployees() {
        java.util.Map<String, Employee> seen = new java.util.LinkedHashMap<>();
        try {
            if (!integrationConfigService.isPlatformEnabled(PLATFORM_TYPE)) {
                log.warn("飞书未启用或未配置，fetch跳过");
                return new java.util.ArrayList<>();
            }
            String tenantToken = getTenantAccessToken();
            if (tenantToken == null) return new java.util.ArrayList<>();
            java.util.List<FeishuDepartment> departments = getAllDepartments(tenantToken);
            java.util.Map<String, String> deptNameMap = new java.util.HashMap<>();
            for (FeishuDepartment d : departments) deptNameMap.put(d.getDepartment_id(), d.getName());
            for (FeishuDepartment dept : departments) {
                String pageToken = null; boolean hasMore = true;
                while (hasMore) {
                    FeishuUserListResponse page = getDepartmentUsers(tenantToken, dept.getDepartment_id(), pageToken);
                    if (page == null || page.getData() == null) break;
                    if (page.getData().getItems() != null) {
                        for (FeishuUser u : page.getData().getItems()) {
                            String key = u.getUser_id();
                            Employee exist = seen.get(key);
                            if (exist == null) {
                                exist = convertToEmployee(u, deptNameMap);
                                exist.setEmployeeId(null);
                                seen.put(key, exist);
                            }
                            if (u.getDepartment_ids() != null) {
                                java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
                                if (exist.getDepartment() != null && !exist.getDepartment().isBlank()) {
                                    names.addAll(java.util.Arrays.asList(exist.getDepartment().split(",")));
                                }
                                for (String depId : u.getDepartment_ids()) {
                                    String name = deptNameMap.get(depId);
                                    if (name != null) names.add(name);
                                }
                                exist.setDepartment(String.join(",", names));
                            }
                        }
                    }
                    hasMore = page.getData().getHas_more();
                    pageToken = page.getData().getPage_token();
                }
            }
        } catch (Exception e) {
            log.error("飞书fetchAllEmployees异常", e);
        }
        return new java.util.ArrayList<>(seen.values());
    }

    @Override
    public java.util.List<com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto> fetchDepartmentTree() {
        java.util.List<com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto> roots = new java.util.ArrayList<>();
        try {
            if (!integrationConfigService.isPlatformEnabled(PLATFORM_TYPE)) return roots;
            String tenantToken = getTenantAccessToken();
            if (tenantToken == null) return roots;
            java.util.List<FeishuDepartment> depts = getAllDepartments(tenantToken);
            java.util.Map<String, com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto> map = new java.util.LinkedHashMap<>();
            for (FeishuDepartment d : depts) {
                com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto node = new com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto();
                node.setPlatformDeptId(d.getDepartment_id());
                node.setName(d.getName());
                node.setParentPlatformDeptId(d.getParent_department_id());
                map.put(d.getDepartment_id(), node);
            }
            for (FeishuDepartment d : depts) {
                com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto node = map.get(d.getDepartment_id());
                if (d.getParent_department_id() == null || d.getParent_department_id().isBlank()) roots.add(node);
                else {
                    com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto parent = map.get(d.getParent_department_id());
                    if (parent != null) parent.getChildren().add(node);
                }
            }
            // members per dept (paged)
            for (FeishuDepartment dept : depts) {
                com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto node = map.get(dept.getDepartment_id());
                String pageToken = null; boolean hasMore = true;
                while (hasMore) {
                    FeishuUserListResponse page = getDepartmentUsers(tenantToken, dept.getDepartment_id(), pageToken);
                    if (page == null || page.getData() == null) break;
                    if (page.getData().getItems() != null) {
                        for (FeishuUser u : page.getData().getItems()) {
                            com.yiyundao.compensation.interfaces.dto.org.OrgMemberPreviewDto m = new com.yiyundao.compensation.interfaces.dto.org.OrgMemberPreviewDto();
                            m.setPlatformUserId(u.getUser_id());
                            m.setName(u.getName());
                            m.setPhone(u.getMobile());
                            m.setEmail(u.getEmail());
                            m.setPosition(u.getJob_title());
                            node.getMembers().add(m);
                        }
                    }
                    hasMore = page.getData().getHas_more();
                    pageToken = page.getData().getPage_token();
                }
            }
        } catch (Exception e) {
            log.error("获取飞书部门树异常", e);
        }
        return roots;
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
        try {
            String tenantToken = getTenantAccessToken();
            if (tenantToken == null || platformUserId == null) {
                return false;
            }

            // 获取用户详情，检查是否是管理员
            String url = API_BASE_URL + "/contact/v3/users/" + platformUserId + "?user_id_type=user_id";

            FeishuUserDetailResponse resp = webClient.get()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(tenantToken))
                    .retrieve()
                    .bodyToMono(FeishuUserDetailResponse.class)
                    .block();

            if (resp != null && resp.getCode() == 0 && resp.getData() != null && resp.getData().getUser() != null) {
                FeishuUser user = resp.getData().getUser();

                // 检查是否是主管理员或部门管理员（基于响应中的可用信息）
                // 飞书用户详情中可能包含is_tenant_manager字段
                try {
                    java.lang.reflect.Method isTenantManager = user.getClass().getMethod("isIsTenantManager");
                    Boolean isManager = (Boolean) isTenantManager.invoke(user);
                    if (Boolean.TRUE.equals(isManager)) {
                        return true;
                    }
                } catch (NoSuchMethodException e) {
                    // 方法不存在，使用备用逻辑
                    log.debug("飞书用户信息不包含is_tenant_manager字段，使用备用管理员判断逻辑");
                }

                // 如果无法确定，默认为非管理员，需要管理员在系统中明确配置
                log.debug("无法确定用户是否为飞书管理员: userId={}", platformUserId);
                return false;
            }

            return false;

        } catch (Exception e) {
            log.error("判断飞书管理员失败: userId={}", platformUserId, e);
            return false;
        }
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
            if (tenantToken == null) {
                log.warn("无法获取飞书访问令牌");
                return;
            }

            // 使用飞书消息接口发送通知
            sendMessage(tenantToken, platformUserId, message);

            log.info("[Feishu] 通知发送完成: userId={}, message={}", platformUserId, message);

        } catch (Exception e) {
            log.error("发送飞书通知失败", e);
        }
    }

    /**
     * 使用飞书消息接口发送消息
     */
    private void sendMessage(String tenantToken, String userId, String message) {
        try {
            // 方法1：使用即时消息接口发送（需要用户ID）
            boolean sent = sendIMMessage(tenantToken, userId, message);

            if (!sent) {
                // 方法2：使用应用消息接口发送（备用方案）
                sendAppMessage(tenantToken, userId, message);
            }

        } catch (Exception e) {
            log.error("[Feishu] 消息发送异常", e);
        }
    }

    /**
     * 使用即时消息接口发送
     */
    private boolean sendIMMessage(String tenantToken, String userId, String message) {
        try {
            String url = API_BASE_URL + "/im/v1/messages";

            // 构建请求参数
            Map<String, Object> params = new HashMap<>();
            params.put("receive_id_type", "open_id");

            Map<String, Object> body = new HashMap<>();
            body.put("receive_id", userId);
            body.put("msg_type", "text");

            Map<String, String> content = new HashMap<>();
            content.put("text", message);
            body.put("content", objectMapper.writeValueAsString(content));

            params.put("body", body);

            // 发送请求 - webClient返回String而不是ResponseEntity
            String responseBody = webClient.post()
                    .uri(url)
                    .headers(h -> {
                        h.setBearerAuth(tenantToken);
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody != null && !responseBody.isEmpty()) {
                com.fasterxml.jackson.databind.JsonNode result = objectMapper.readTree(responseBody);
                if (result.has("code") && result.get("code").asInt() == 0) {
                    log.info("[Feishu] 即时消息发送成功: userId={}", userId);
                    return true;
                } else {
                    log.warn("[Feishu] 即时消息发送失败: code={}, msg={}",
                            result.has("code") ? result.get("code") : "N/A",
                            result.has("msg") ? result.get("msg") : "N/A");
                }
            }

            return false;

        } catch (Exception e) {
            log.error("[Feishu] 即时消息发送异常", e);
            return false;
        }
    }

    /**
     * 使用应用消息接口发送
     */
    private void sendAppMessage(String tenantToken, String userId, String message) {
        try {
            FeishuConfigDto cfg = integrationConfigService.getFeishuConfig();
            String appToken = cfg != null && cfg.getAppToken() != null ? cfg.getAppToken() : null;

            if (appToken == null || appToken.isBlank()) {
                log.warn("[Feishu] 应用Token未配置，无法发送应用消息");
                return;
            }

            // 首先创建消息
            String url = API_BASE_URL + "/im/v1/messages";

            Map<String, Object> params = new HashMap<>();
            params.put("receive_id_type", "open_id");

            Map<String, Object> body = new HashMap<>();
            body.put("receive_id", userId);
            body.put("msg_type", "text");

            Map<String, String> content = new HashMap<>();
            content.put("text", "【薪酬助手通知】" + message);
            body.put("content", objectMapper.writeValueAsString(content));

            params.put("body", body);

            // 发送请求创建消息 - webClient返回String而不是ResponseEntity
            String responseBody = webClient.post()
                    .uri(url)
                    .headers(h -> {
                        h.setBearerAuth(tenantToken);
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody != null && !responseBody.isEmpty()) {
                com.fasterxml.jackson.databind.JsonNode result = objectMapper.readTree(responseBody);
                if (result.has("code") && result.get("code").asInt() == 0) {
                    log.info("[Feishu] 应用消息发送成功: userId={}", userId);
                } else {
                    log.warn("[Feishu] 应用消息发送失败: code={}",
                            result.has("code") ? result.get("code") : "N/A");
                }
            }

        } catch (Exception e) {
            log.error("[Feishu] 应用消息发送异常", e);
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

    @SuppressWarnings("unused")
    private static class FeishuTokenRequest {
        private String app_id;
        private String app_secret;
        public FeishuTokenRequest(String appId, String appSecret) { this.app_id = appId; this.app_secret = appSecret; }
        public String getApp_id() { return app_id; }
        public String getApp_secret() { return app_secret; }
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    private static class FeishuUserDetailData {
        private FeishuUser user;
        public FeishuUser getUser() { return user; }
        public void setUser(FeishuUser user) { this.user = user; }
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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
