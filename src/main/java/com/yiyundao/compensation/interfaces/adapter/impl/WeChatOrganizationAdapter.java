package com.yiyundao.compensation.interfaces.adapter.impl;

import com.yiyundao.compensation.interfaces.adapter.OrganizationAdapter;
import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yiyundao.compensation.interfaces.dto.config.WechatConfigDto;
import com.yiyundao.compensation.service.PlatformTokenCacheService;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 企业微信组织同步适配器（已迁移至 interfaces/adapter/impl）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatOrganizationAdapter implements OrganizationAdapter {

    private final EmployeeService employeeService;
    private final WebClient webClient;
    private final IntegrationConfigService integrationConfigService;
    private final PlatformTokenCacheService platformTokenCacheService;
    private final SysConfigService sysConfigService;

    @Value("${wechat.corp-id:}")
    private String corpId;

    @Value("${wechat.corp-secret:}")
    private String corpSecret;

    @Value("${wechat.agent-id:}")
    private String agentId;

    private static final String PLATFORM_TYPE = "wechat";
    private static final String API_BASE_URL = "https://qyapi.weixin.qq.com/cgi-bin";

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    public OrganizationSyncResult syncOrganization() {
        log.info("开始企业微信组织架构同步");

        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                return OrganizationSyncResult.failure(PLATFORM_TYPE, "获取访问令牌失败", null);
            }

            List<Department> departments = getDepartmentList(accessToken);

            int newCount = 0;
            int updateCount = 0;
            int totalCount = 0;
            List<String> errors = new ArrayList<>();

            for (Department dept : departments) {
                try {
                    List<WeChatUser> users = getDepartmentUsers(accessToken, dept.getId());
                    totalCount += users.size();

                    for (WeChatUser user : users) {
                        try {
                            Employee existingEmployee = employeeService.getByPlatformUserId(user.getUserid(), PLATFORM_TYPE);

                            if (existingEmployee == null) {
                                Employee candidate = convertToEmployee(user, dept);
                                // 按工号回填已有员工
                                if (candidate.getEmployeeId() != null) {
                                    Employee byEmpId = employeeService.getByEmployeeId(candidate.getEmployeeId());
                                    if (byEmpId != null) {
                                        Employee update = candidate;
                                        employeeService.updateEmployee(byEmpId.getId(), update);
                                        updateCount++;
                                        log.debug("回填并更新企微员工(通过工号匹配): {}", candidate.getName());
                                        continue;
                                    }
                                }
                                employeeService.createEmployee(candidate);
                                newCount++;
                                log.debug("新增企微员工: {}", user.getName());
                            } else {
                                Employee updateInfo = convertToEmployee(user, dept);
                                employeeService.updateEmployee(existingEmployee.getId(), updateInfo);
                                updateCount++;
                                log.debug("更新企微员工: {}", user.getName());
                            }

                        } catch (Exception e) {
                            String error = "同步员工失败: " + user.getName() + " - " + e.getMessage();
                            errors.add(error);
                            log.error("同步企微员工失败: {}", user.getName(), e);
                        }
                    }

                } catch (Exception e) {
                    String error = "同步部门失败: " + dept.getName() + " - " + e.getMessage();
                    errors.add(error);
                    log.error("同步企微部门失败: {}", dept.getName(), e);
                }
            }

            log.info("企业微信组织架构同步完成: 总计{}人, 新增{}人, 更新{}人, 错误{}个",
                    totalCount, newCount, updateCount, errors.size());

            OrganizationSyncResult result = OrganizationSyncResult.success(PLATFORM_TYPE, totalCount, newCount, updateCount);
            if (!errors.isEmpty()) {
                result.setErrors(errors);
            }

            return result;

        } catch (Exception e) {
            log.error("企业微信组织架构同步异常", e);
            return OrganizationSyncResult.failure(PLATFORM_TYPE, "同步异常: " + e.getMessage(), null);
        }
    }

    @Override
    public Employee getUserInfo(String platformUserId) {
        log.info("获取企微用户信息: {}", platformUserId);

        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                return null;
            }

            String url = API_BASE_URL + "/user/get?access_token=" + accessToken + "&userid=" + platformUserId;

            WeChatUserResponse response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(WeChatUserResponse.class)
                    .block();

            if (response != null && response.getErrcode() == 0) {
                WeChatUser user = response.getUser();
                return convertToEmployee(user, null);
            }

        } catch (Exception e) {
            log.error("获取企微用户信息失败: {}", platformUserId, e);
        }

        return null;
    }

    @Override
    public boolean isManager(String platformUserId) {
        log.info("检查企微用户管理员权限: {}", platformUserId);
        return true;
    }

    @Override
    public List<Employee> getDepartmentEmployees(String departmentId) {
        log.info("获取企微部门员工: {}", departmentId);

        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                return new ArrayList<>();
            }

            List<WeChatUser> users = getDepartmentUsers(accessToken, departmentId);
            List<Employee> employees = new ArrayList<>();

            for (WeChatUser user : users) {
                Employee employee = convertToEmployee(user, null);
                employees.add(employee);
            }

            return employees;

        } catch (Exception e) {
            log.error("获取企微部门员工失败: {}", departmentId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void sendApprovalNotification(String platformUserId, String message) {
        log.info("发送企微审批通知: userId={}, message={}", platformUserId, message);

        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                log.error("获取访问令牌失败，无法发送通知");
                return;
            }

            String messageBody = String.format(
                    "{\"touser\":\"%s\",\"msgtype\":\"text\",\"agentid\":%s,\"text\":{\"content\":\"%s\"}}",
                    platformUserId, resolveAgentId(), message);

            String url = API_BASE_URL + "/message/send?access_token=" + accessToken;

            webClient.post()
                    .uri(url)
                    .bodyValue(messageBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(response -> log.info("企微通知发送结果: {}", response));

        } catch (Exception e) {
            log.error("发送企微审批通知失败", e);
        }
    }

    @Override
    public boolean checkConnection() {
        try {
            String accessToken = getAccessToken();
            return accessToken != null;
        } catch (Exception e) {
            log.error("检查企微连接失败", e);
            return false;
        }
    }

    private String getAccessToken() {
        try {
            WechatConfigDto cfg = integrationConfigService.getWechatConfig();
            String id = (cfg != null && cfg.getCorpId() != null && !cfg.getCorpId().isBlank()) ? cfg.getCorpId() : corpId;
            String secret = (cfg != null && cfg.getCorpSecret() != null && !cfg.getCorpSecret().isBlank()) ? cfg.getCorpSecret() : corpSecret;
            boolean placeholder = (id != null && id.toLowerCase().startsWith("your_")) || (secret != null && secret.toLowerCase().startsWith("your_"));
            if (placeholder) {
                log.warn("未配置 wechat.corp-id/corp-secret（占位），跳过获取token");
                return null;
            }
            // cache first
            String cached = platformTokenCacheService.getToken(PLATFORM_TYPE);
            if (cached != null && !cached.isBlank()) {
                return cached;
            }
            String url = API_BASE_URL + "/gettoken?corpid=" + id + "&corpsecret=" + secret;

            WeChatTokenResponse response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(WeChatTokenResponse.class)
                    .block();

            if (response != null && response.getErrcode() == 0) {
                String token = response.getAccess_token();
                Integer e = response.getExpires_in();
                int buffer = sysConfigService.getInt("oauth.token.ttl.buffer.seconds", 300);
                long ttl = (e != null ? e : 7200) - Math.max(0, buffer);
                if (ttl < 60) ttl = 60;
                platformTokenCacheService.setToken(PLATFORM_TYPE, token, ttl);
                return token;
            } else {
                log.error("获取企微访问令牌失败: {}", response != null ? response.getErrmsg() : "null response");
                return null;
            }

        } catch (Exception e) {
            log.error("获取企微访问令牌异常", e);
            return null;
        }
    }

    private String resolveAgentId() {
        WechatConfigDto cfg = integrationConfigService.getWechatConfig();
        if (cfg != null && cfg.getAgentId() != null && !cfg.getAgentId().isBlank()) return cfg.getAgentId();
        return agentId;
    }

    private List<Department> getDepartmentList(String accessToken) {
        try {
            String url = API_BASE_URL + "/department/list?access_token=" + accessToken;
            WeChatDepartmentListResponse resp = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(WeChatDepartmentListResponse.class)
                    .block();
            if (resp != null && resp.getErrcode() == 0 && resp.getDepartment() != null) {
                List<Department> list = new ArrayList<>();
                for (WeChatDepartment d : resp.getDepartment()) {
                    list.add(new Department(String.valueOf(d.getId()), d.getName()));
                }
                return list;
            }
            log.warn("获取企微部门列表失败: {}", resp != null ? resp.getErrmsg() : "null response");
        } catch (Exception e) {
            log.error("获取企微部门列表异常", e);
        }
        return new ArrayList<>();
    }

    private List<WeChatUser> getDepartmentUsers(String accessToken, String departmentId) {
        try {
            String url = API_BASE_URL + "/user/list?access_token=" + accessToken + "&department_id=" + departmentId + "&fetch_child=1";
            WeChatUserListResponse resp = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(WeChatUserListResponse.class)
                    .block();
            if (resp != null && resp.getErrcode() == 0 && resp.getUserlist() != null) {
                return resp.getUserlist();
            }
            log.warn("获取企微部门用户失败: departmentId={}, err={}", departmentId, resp != null ? resp.getErrmsg() : "null response");
        } catch (Exception e) {
            log.error("获取企微部门用户异常: {}", departmentId, e);
        }
        return new ArrayList<>();
    }

    private Employee convertToEmployee(WeChatUser user, Department dept) {
        Employee employee = new Employee();
        String empId = (user.getAlias() != null && !user.getAlias().isBlank()) ? user.getAlias() : user.getUserid();
        employee.setEmployeeId(empId);
        employee.setName(user.getName());
        employee.setPhone(user.getMobile());
        employee.setEmail(user.getEmail());
        employee.setPlatformUserId(user.getUserid());
        employee.setPlatformType(PLATFORM_TYPE);
        employee.setDepartment(dept != null ? dept.getName() : null);
        employee.setPosition(user.getPosition());
        employee.setStatus("active");
        employee.setOffline(false);
        return employee;
    }

    // 内部类定义
    private static class WeChatTokenResponse {
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

    private static class WeChatUserResponse {
        private int errcode;
        private String errmsg;
        private WeChatUser user;

        public int getErrcode() { return errcode; }
        public void setErrcode(int errcode) { this.errcode = errcode; }
        public String getErrmsg() { return errmsg; }
        public void setErrmsg(String errmsg) { this.errmsg = errmsg; }
        public WeChatUser getUser() { return user; }
        public void setUser(WeChatUser user) { this.user = user; }
    }

    private static class WeChatUser {
        private String userid;
        private String name;
        private String mobile;
        private String email;
        private String position;
        private List<Integer> department;
        private String alias;

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
        public List<Integer> getDepartment() { return department; }
        public void setDepartment(List<Integer> department) { this.department = department; }
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
    }

    private static class WeChatUserListResponse {
        private int errcode;
        private String errmsg;
        private List<WeChatUser> userlist;

        public int getErrcode() { return errcode; }
        public void setErrcode(int errcode) { this.errcode = errcode; }
        public String getErrmsg() { return errmsg; }
        public void setErrmsg(String errmsg) { this.errmsg = errmsg; }
        public List<WeChatUser> getUserlist() { return userlist; }
        public void setUserlist(List<WeChatUser> userlist) { this.userlist = userlist; }
    }

    private static class WeChatDepartmentListResponse {
        private int errcode;
        private String errmsg;
        private List<WeChatDepartment> department;

        public int getErrcode() { return errcode; }
        public void setErrcode(int errcode) { this.errcode = errcode; }
        public String getErrmsg() { return errmsg; }
        public void setErrmsg(String errmsg) { this.errmsg = errmsg; }
        public List<WeChatDepartment> getDepartment() { return department; }
        public void setDepartment(List<WeChatDepartment> department) { this.department = department; }
    }

    private static class WeChatDepartment {
        private int id;
        private String name;
        private int parentid;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getParentid() { return parentid; }
        public void setParentid(int parentid) { this.parentid = parentid; }
    }

    private static class Department {
        private String id;
        private String name;

        public Department(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
