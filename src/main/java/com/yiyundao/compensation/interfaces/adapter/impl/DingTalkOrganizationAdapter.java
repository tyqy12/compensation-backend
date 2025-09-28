package com.yiyundao.compensation.interfaces.adapter.impl;

import com.yiyundao.compensation.interfaces.adapter.OrganizationAdapter;
import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yiyundao.compensation.interfaces.dto.config.DingTalkConfigDto;
import com.yiyundao.compensation.service.PlatformTokenCacheService;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DingTalkOrganizationAdapter implements OrganizationAdapter {

    private final EmployeeService employeeService;
    private final WebClient webClient;
    private final IntegrationConfigService integrationConfigService;
    private final PlatformTokenCacheService platformTokenCacheService;
    private final SysConfigService sysConfigService;

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

            List<DingDepartment> departments = getDepartments(accessToken);

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
                                Employee existing = employeeService.getByPlatformUserId(user.getUserid(), PLATFORM_TYPE);
                                Employee candidate = convertToEmployee(user, deptNameMap);
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
        // TODO: 判断钉钉管理员
        return true;
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
        e.setPlatformUserId(user.getUserid());
        e.setPlatformType(PLATFORM_TYPE);
        if (deptNameMap != null && user.getDepartment() != null && !user.getDepartment().isEmpty()) {
            String name = deptNameMap.getOrDefault(user.getDepartment().get(0), null);
            e.setDepartment(name);
        }
        e.setPosition(user.getPosition());
        e.setStatus(Boolean.TRUE.equals(user.getActive()) ? "active" : "inactive");
        e.setOffline(false);
        return e;
    }

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
