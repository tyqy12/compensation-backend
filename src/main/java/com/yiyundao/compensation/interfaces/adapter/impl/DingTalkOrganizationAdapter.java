package com.yiyundao.compensation.interfaces.adapter.impl;

import com.yiyundao.compensation.interfaces.adapter.OrganizationAdapter;
import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yiyundao.compensation.interfaces.dto.config.DingTalkConfigDto;
import com.yiyundao.compensation.service.PlatformTokenCacheService;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import com.yiyundao.compensation.modules.user.service.UserBindingService;
import com.yiyundao.compensation.modules.org.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DingTalkOrganizationAdapter implements OrganizationAdapter {

    private final ObjectProvider<EmployeeService> employeeServiceProvider;
    private final ObjectProvider<UserBindingService> userBindingServiceProvider;
    private final WebClient webClient;
    private final IntegrationConfigService integrationConfigService;
    private final PlatformTokenCacheService platformTokenCacheService;
    private final SysConfigService sysConfigService;
    private final DepartmentService departmentService;
    private final RestTemplate restTemplate;

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
            if (!integrationConfigService.isPlatformEnabled(PLATFORM_TYPE)) {
                log.warn("钉钉未启用或未配置，跳过同步");
                return OrganizationSyncResult.failure(PLATFORM_TYPE, "未启用或未配置，跳过", null);
            }
            String accessToken = getAccessToken();
            if (accessToken == null) {
                return OrganizationSyncResult.failure(PLATFORM_TYPE, "获取访问令牌失败", null);
            }

            List<DingDepartment> departments = getDepartments(accessToken);
            for (DingDepartment d : departments) {
                String parentPid = (d.getParentid() == null || d.getParentid() <= 1) ? null : String.valueOf(d.getParentid());
                departmentService.upsert(PLATFORM_TYPE, String.valueOf(d.getId()), d.getName(), parentPid, null);
            }

            int total = 0, created = 0, updated = 0;
            List<String> errors = new ArrayList<>();

            Map<Long, String> deptNameMap = new HashMap<>();
            for (DingDepartment d : departments) {
                deptNameMap.put(d.getId(), d.getName());
            }

            for (DingDepartment dept : departments) {
                long offset = 0;
                int size = 100;
                boolean hasMore = true;
                while (hasMore) {
                    DingUserListResponse page = getDepartmentUsers(accessToken, dept.getId(), offset, size);
                    if (page == null) break;
                    if (page.getUserlist() != null) {
                        total += page.getUserlist().size();
                        for (DingUser user : page.getUserlist()) {
                            try {
                                Employee existing = employeeServiceProvider.getObject()
                                        .getByProviderAndSubjectId(PLATFORM_TYPE, user.getUserid());
                                Employee candidate = convertToEmployee(user, deptNameMap);
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
                                errors.add("同步用户失败: " + user.getName() + ", err=" + ex.getMessage());
                                log.error("同步钉钉员工失败: {}", user.getName(), ex);
                            }
                        }
                    }
                    hasMore = page.getHasMore() != null && page.getHasMore();
                    offset += size;
                }
            }

            OrganizationSyncResult result = OrganizationSyncResult.success(PLATFORM_TYPE, total, created, updated);
            if (!errors.isEmpty()) result.setErrors(errors);
            return result;
        } catch (Exception e) {
            log.error("钉钉组织架构同步异常", e);
            return OrganizationSyncResult.failure(PLATFORM_TYPE, "同步异常: " + e.getMessage(), null);
        }
    }

    @Override
    public java.util.List<Employee> fetchAllEmployees() {
        java.util.Map<String, Employee> seen = new java.util.LinkedHashMap<>();
        try {
            if (!integrationConfigService.isPlatformEnabled(PLATFORM_TYPE)) {
                log.warn("钉钉未启用或未配置，fetch跳过");
                return new java.util.ArrayList<>();
            }
            String accessToken = getAccessToken();
            if (accessToken == null) return new java.util.ArrayList<>();
            java.util.List<DingDepartment> departments = getDepartments(accessToken);
            java.util.Map<Long, String> deptNameMap = new java.util.HashMap<>();
            for (DingDepartment d : departments) deptNameMap.put(d.getId(), d.getName());
            for (DingDepartment dept : departments) {
                long offset = 0; int size = 100; boolean hasMore = true;
                while (hasMore) {
                    DingUserListResponse page = getDepartmentUsers(accessToken, dept.getId(), offset, size);
                    if (page == null) break;
                    if (page.getUserlist() != null) {
                        for (DingUser u : page.getUserlist()) {
                            String key = u.getUserid();
                            Employee exist = seen.get(key);
                            if (exist == null) {
                                exist = convertToEmployee(u, deptNameMap);
                                exist.setEmployeeId(null);
                                seen.put(key, exist);
                            }
                            if (u.getDepartment() != null) {
                                java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
                                if (exist.getDepartment() != null && !exist.getDepartment().isBlank()) {
                                    names.addAll(java.util.Arrays.asList(exist.getDepartment().split(",")));
                                }
                                for (Long depId : u.getDepartment()) {
                                    String name = deptNameMap.get(depId);
                                    if (name != null) names.add(name);
                                }
                                exist.setDepartment(String.join(",", names));
                            }
                        }
                    }
                    hasMore = page.getHasMore() != null && page.getHasMore();
                    offset += size;
                }
            }
        } catch (Exception e) {
            log.error("钉钉fetchAllEmployees异常", e);
        }
        return new java.util.ArrayList<>(seen.values());
    }

    @Override
    public java.util.List<com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto> fetchDepartmentTree() {
        java.util.List<com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto> roots = new java.util.ArrayList<>();
        try {
            if (!integrationConfigService.isPlatformEnabled(PLATFORM_TYPE)) return roots;
            String accessToken = getAccessToken();
            if (accessToken == null) return roots;
            java.util.List<DingDepartment> depts = getDepartments(accessToken);
            java.util.Map<Long, com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto> map = new java.util.LinkedHashMap<>();
            for (DingDepartment d : depts) {
                com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto node = new com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto();
                node.setPlatformDeptId(String.valueOf(d.getId()));
                node.setName(d.getName());
                node.setParentPlatformDeptId(d.getParentid() != null && d.getParentid() > 1 ? String.valueOf(d.getParentid()) : null);
                map.put(d.getId(), node);
            }
            for (DingDepartment d : depts) {
                com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto node = map.get(d.getId());
                if (d.getParentid() == null || d.getParentid() <= 1) roots.add(node);
                else {
                    com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto parent = map.get(d.getParentid());
                    if (parent != null) parent.getChildren().add(node);
                }
            }
            // members per dept (paged)
            for (DingDepartment dept : depts) {
                com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto node = map.get(dept.getId());
                long offset = 0; int size = 100; boolean hasMore = true;
                while (hasMore) {
                    DingUserListResponse page = getDepartmentUsers(accessToken, dept.getId(), offset, size);
                    if (page == null) break;
                    if (page.getUserlist() != null) {
                        for (DingUser u : page.getUserlist()) {
                            com.yiyundao.compensation.interfaces.dto.org.OrgMemberPreviewDto m = new com.yiyundao.compensation.interfaces.dto.org.OrgMemberPreviewDto();
                            m.setProvider(PLATFORM_TYPE);
                            m.setSubjectId(u.getUserid());
                            m.setName(u.getName());
                            m.setPhone(u.getMobile());
                            m.setEmail(u.getEmail());
                            m.setPosition(u.getPosition());
                            node.getMembers().add(m);
                        }
                    }
                    hasMore = page.getHasMore() != null && page.getHasMore();
                    offset += size;
                }
            }
        } catch (Exception e) {
            log.error("获取钉钉部门树异常", e);
        }
        return roots;
    }

    @Override
    public Employee getUserInfo(String platformUserId) {
        try {
            String accessToken = getAccessToken();
            if (accessToken == null) return null;
            String url = API_BASE_URL + "/user/get?access_token=" + accessToken + "&userid=" + platformUserId;
            DingUserDetailResponse resp = webClient.get().uri(url).retrieve().bodyToMono(DingUserDetailResponse.class).block();
            if (resp != null && resp.getErrcode() == 0 && resp.getUserid() != null) {
                DingUser user = new DingUser();
                user.setUserid(resp.getUserid());
                user.setName(resp.getName());
                user.setMobile(resp.getMobile());
                user.setEmail(resp.getEmail());
                user.setPosition(resp.getPosition());
                user.setJobnumber(resp.getJobnumber());
                return convertToEmployee(user, null);
            }
        } catch (Exception e) {
            log.error("获取钉钉用户信息失败: {}", platformUserId, e);
        }
        return null;
    }

    @Override
    public boolean isManager(String platformUserId) {
        try {
            String accessToken = getAccessToken();
            if (accessToken == null || platformUserId == null) {
                return false;
            }

            // 获取用户详情，检查是否是管理员
            String url = API_BASE_URL + "/user/get?access_token=" + accessToken + "&userid=" + platformUserId;
            String respJson = restTemplate.getForObject(url, String.class);

            if (respJson != null && !respJson.isBlank()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(respJson);

                // 检查用户是否为主管理员
                if (root.has("isBoss") && root.get("isBoss").asBoolean()) {
                    return true;
                }

                // 检查是否是部门管理员
                if (root.has("isAdmin") && root.get("isAdmin").asBoolean()) {
                    return true;
                }

                // 检查角色列表中是否包含管理员角色
                if (root.has("roles") && root.get("roles").isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode role : root.get("roles")) {
                        if (role.has("id")) {
                            String roleId = role.get("id").asText();
                            // 钉钉管理员角色ID通常为1
                            if ("1".equals(roleId)) {
                                return true;
                            }
                        }
                    }
                }
            }

            log.debug("用户不是钉钉管理员: userId={}", platformUserId);
            return false;

        } catch (Exception e) {
            log.error("判断钉钉管理员失败: userId={}", platformUserId, e);
            return false;
        }
    }

    @Override
    public List<Employee> getDepartmentEmployees(String departmentId) {
        List<Employee> list = new ArrayList<>();
        try {
            String accessToken = getAccessToken();
            if (accessToken == null) return list;
            long offset = 0; int size = 100; boolean hasMore = true;
            while (hasMore) {
                DingUserListResponse page = getDepartmentUsers(accessToken, Long.parseLong(departmentId), offset, size);
                if (page == null) break;
                if (page.getUserlist() != null) {
                    for (DingUser u : page.getUserlist()) {
                        list.add(convertToEmployee(u, null));
                    }
                }
                hasMore = page.getHasMore() != null && page.getHasMore();
                offset += size;
            }
        } catch (Exception e) {
            log.error("获取钉钉部门员工失败: {}", departmentId, e);
        }
        return list;
    }

    @Override
    public void sendApprovalNotification(String platformUserId, String message) {
        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                log.warn("无法获取钉钉访问令牌");
                return;
            }

            // 方法1：使用工作通知发送消息（需要企业应用权限）
            boolean sent = sendWorkNotification(accessToken, platformUserId, message);

            if (!sent) {
                // 方法2：使用机器人Webhook发送消息（备用方案）
                sendRobotNotification(platformUserId, message);
            }

            log.info("[DingTalk] 通知发送完成: userId={}, message={}", platformUserId, message);

        } catch (Exception e) {
            log.error("发送钉钉通知失败", e);
        }
    }

    /**
     * 使用工作通知发送消息
     */
    private boolean sendWorkNotification(String accessToken, String userId, String message) {
        try {
            String url = API_BASE_URL + "/message/send_to_user?access_token=" + accessToken;

            // 构建消息体
            Map<String, Object> msgBody = new HashMap<>();
            msgBody.put("userid", userId);
            msgBody.put("msgtype", "text");

            Map<String, String> textContent = new HashMap<>();
            textContent.put("content", message);
            msgBody.put("text", textContent);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(msgBody, headers);

            // 发送请求
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode result = mapper.readTree(response.getBody());
                if (result.has("errcode") && result.get("errcode").asInt() == 0) {
                    log.info("[DingTalk] 工作通知发送成功: userId={}", userId);
                    return true;
                } else {
                    log.warn("[DingTalk] 工作通知发送失败: errcode={}, errmsg={}",
                            result.has("errcode") ? result.get("errcode") : "N/A",
                            result.has("errmsg") ? result.get("errmsg") : "N/A");
                }
            }

            return false;

        } catch (Exception e) {
            log.error("[DingTalk] 工作通知发送异常", e);
            return false;
        }
    }

    /**
     * 使用机器人Webhook发送消息
     */
    private void sendRobotNotification(String userId, String message) {
        try {
            DingTalkConfigDto cfg = integrationConfigService.getDingTalkConfig();
            String webhookUrl = cfg != null && cfg.getWebhookUrl() != null ? cfg.getWebhookUrl() : null;

            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.warn("[DingTalk] 机器人Webhook URL未配置，无法发送机器人消息");
                return;
            }

            // 构建消息体（机器人不支持@指定用户，使用纯文本消息）
            Map<String, Object> msgBody = new HashMap<>();
            msgBody.put("msgtype", "text");

            Map<String, String> textContent = new HashMap<>();
            textContent.put("content", "【薪酬助手通知】" + message);
            msgBody.put("text", textContent);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(msgBody, headers);

            // 发送请求
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode result = mapper.readTree(response.getBody());
                if (result.has("errcode") && result.get("errcode").asInt() == 0) {
                    log.info("[DingTalk] 机器人消息发送成功");
                } else {
                    log.warn("[DingTalk] 机器人消息发送失败: errcode={}",
                            result.has("errcode") ? result.get("errcode") : "N/A");
                }
            }

        } catch (Exception e) {
            log.error("[DingTalk] 机器人消息发送异常", e);
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
            DingTalkConfigDto cfg = integrationConfigService.getDingTalkConfig();
            String key = (cfg != null && cfg.getAppKey() != null && !cfg.getAppKey().isBlank()) ? cfg.getAppKey() : appKey;
            String secret = (cfg != null && cfg.getAppSecret() != null && !cfg.getAppSecret().isBlank()) ? cfg.getAppSecret() : appSecret;
            boolean placeholder = (key != null && key.toLowerCase().startsWith("your_")) || (secret != null && secret.toLowerCase().startsWith("your_"));
            if (placeholder || key == null || key.isBlank() || secret == null || secret.isBlank()) {
                log.warn("未配置 dingtalk.app-key/app-secret，跳过获取token");
                return null;
            }
            // cache first
            String cached = platformTokenCacheService.getToken(PLATFORM_TYPE);
            if (cached != null && !cached.isBlank()) return cached;
            String url = API_BASE_URL + "/gettoken?appkey=" + key + "&appsecret=" + secret;
            DingTokenResponse resp = webClient.get().uri(url).retrieve().bodyToMono(DingTokenResponse.class).block();
            if (resp != null && resp.getErrcode() == 0) {
                String token = resp.getAccess_token();
                int buffer = sysConfigService.getInt("oauth.token.ttl.buffer.seconds", 300);
                long ttl = resp.getExpires_in() != null ? Math.max(60, resp.getExpires_in() - Math.max(0, buffer)) : 6900;
                platformTokenCacheService.setToken(PLATFORM_TYPE, token, ttl);
                return token;
            }
            log.error("获取钉钉访问令牌失败: {}", resp != null ? resp.getErrmsg() : "null response");
            return null;
        } catch (Exception e) {
            log.error("获取钉钉访问令牌异常", e);
            return null;
        }
    }

    private List<DingDepartment> getDepartments(String accessToken) {
        try {
            String url = API_BASE_URL + "/department/list?access_token=" + accessToken;
            DingDepartmentListResponse resp = webClient.get().uri(url).retrieve().bodyToMono(DingDepartmentListResponse.class).block();
            if (resp != null && resp.getErrcode() == 0 && resp.getDepartment() != null) return resp.getDepartment();
            log.warn("获取钉钉部门失败: {}", resp != null ? resp.getErrmsg() : "null response");
        } catch (Exception e) {
            log.error("获取钉钉部门异常", e);
        }
        return new ArrayList<>();
    }

    private DingUserListResponse getDepartmentUsers(String accessToken, long departmentId, long offset, int size) {
        try {
            String url = API_BASE_URL + "/user/listbypage?access_token=" + accessToken +
                    "&department_id=" + departmentId + "&offset=" + offset + "&size=" + size;
            return webClient.get().uri(url).retrieve().bodyToMono(DingUserListResponse.class).block();
        } catch (Exception e) {
            log.error("分页获取钉钉用户失败: deptId={}, offset={} size={}", departmentId, offset, size, e);
            return null;
        }
    }

    private Employee convertToEmployee(DingUser user, Map<Long, String> deptNameMap) {
        Employee e = new Employee();
        String empId = (user.getJobnumber() != null && !user.getJobnumber().isBlank()) ? user.getJobnumber() : user.getUserid();
        e.setEmployeeId(empId);
        e.setName(user.getName());
        e.setPhone(user.getMobile());
        e.setEmail(user.getEmail());
        e.setSubjectId(user.getUserid());
        e.setProvider(PLATFORM_TYPE);
        if (deptNameMap != null && user.getDepartment() != null && !user.getDepartment().isEmpty()) {
            String name = deptNameMap.getOrDefault(user.getDepartment().get(0), null);
            e.setDepartment(name);
        }
        e.setPosition(user.getPosition());
        e.setStatus(Boolean.TRUE.equals(user.getActive()) ? "active" : "inactive");
        e.setOffline(false);
        return e;
    }

    @SuppressWarnings("unused")
    private static class DingTokenResponse {
        private int errcode;
        private String errmsg;
        private String access_token;
        private Integer expires_in;

        public int getErrcode() { return errcode; }
        public void setErrcode(int errcode) { this.errcode = errcode; }
        public String getErrmsg() { return errmsg; }
        public void setErrmsg(String errmsg) { this.errmsg = errmsg; }
        public String getAccess_token() { return access_token; }
        public void setAccess_token(String access_token) { this.access_token = access_token; }
        public Integer getExpires_in() { return expires_in; }
        public void setExpires_in(Integer expires_in) { this.expires_in = expires_in; }
    }

    @SuppressWarnings("unused")
    private static class DingDepartmentListResponse {
        private int errcode;
        private String errmsg;
        private List<DingDepartment> department;
        public int getErrcode() { return errcode; }
        public void setErrcode(int errcode) { this.errcode = errcode; }
        public String getErrmsg() { return errmsg; }
        public void setErrmsg(String errmsg) { this.errmsg = errmsg; }
        public List<DingDepartment> getDepartment() { return department; }
        public void setDepartment(List<DingDepartment> department) { this.department = department; }
    }

    @SuppressWarnings("unused")
    private static class DingDepartment {
        private Long id;
        private String name;
        private Long parentid;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getParentid() { return parentid; }
        public void setParentid(Long parentid) { this.parentid = parentid; }
    }

    @SuppressWarnings("unused")
    private static class DingUserListResponse {
        private int errcode;
        private String errmsg;
        private Boolean hasMore;
        private List<DingUser> userlist;
        public int getErrcode() { return errcode; }
        public void setErrcode(int errcode) { this.errcode = errcode; }
        public String getErrmsg() { return errmsg; }
        public void setErrmsg(String errmsg) { this.errmsg = errmsg; }
        public Boolean getHasMore() { return hasMore; }
        public void setHasMore(Boolean hasMore) { this.hasMore = hasMore; }
        public List<DingUser> getUserlist() { return userlist; }
        public void setUserlist(List<DingUser> userlist) { this.userlist = userlist; }
    }

    @SuppressWarnings("unused")
    private static class DingUserDetailResponse {
        private int errcode;
        private String errmsg;
        private String userid;
        private String name;
        private String mobile;
        private String email;
        private String position;
        private String jobnumber;
        public int getErrcode() { return errcode; }
        public void setErrcode(int errcode) { this.errcode = errcode; }
        public String getErrmsg() { return errmsg; }
        public void setErrmsg(String errmsg) { this.errmsg = errmsg; }
        public String getUserid() { return userid; }
        public void setUserid(String userid) { this.userid = userid; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }
        public String getJobnumber() { return jobnumber; }
        public void setJobnumber(String jobnumber) { this.jobnumber = jobnumber; }
    }

    @SuppressWarnings("unused")
    private static class DingUser {
        private String userid;
        private String name;
        private String mobile;
        private String email;
        private String position;
        private String jobnumber;
        private List<Long> department;
        private Boolean active;
        public String getUserid() { return userid; }
        public void setUserid(String userid) { this.userid = userid; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }
        public String getJobnumber() { return jobnumber; }
        public void setJobnumber(String jobnumber) { this.jobnumber = jobnumber; }
        public List<Long> getDepartment() { return department; }
        public void setDepartment(List<Long> department) { this.department = department; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }
}
