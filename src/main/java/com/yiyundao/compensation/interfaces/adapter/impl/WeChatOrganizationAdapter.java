package com.yiyundao.compensation.interfaces.adapter.impl;

import com.yiyundao.compensation.interfaces.adapter.OrganizationAdapter;
import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yiyundao.compensation.interfaces.dto.config.WechatConfigDto;
import com.yiyundao.compensation.service.PlatformTokenCacheService;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import com.yiyundao.compensation.modules.user.service.UserBindingService;
import com.yiyundao.compensation.modules.org.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 企业微信组织同步适配器（配置优先从数据库读取）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatOrganizationAdapter implements OrganizationAdapter {

    private final ObjectProvider<EmployeeService> employeeServiceProvider;
    private final ObjectProvider<UserBindingService> userBindingServiceProvider;
    private final WebClient webClient;
    private final IntegrationConfigService integrationConfigService;
    private final PlatformTokenCacheService platformTokenCacheService;
    private final SysConfigService sysConfigService;
    private final DepartmentService departmentService;

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
            if (!integrationConfigService.isPlatformEnabled(PLATFORM_TYPE)) {
                log.warn("企业微信未启用或未配置，跳过同步");
                return OrganizationSyncResult.failure(PLATFORM_TYPE, "未启用或未配置，跳过", null);
            }
            String accessToken = getAccessToken();
            if (accessToken == null) {
                return OrganizationSyncResult.failure(PLATFORM_TYPE, "获取访问令牌失败", null);
            }

            List<WeChatDepartment> rawDepts = getWeChatDepartmentRawList(accessToken);
            for (WeChatDepartment d : rawDepts) {
                String parentPid = (d.getParentid() <= 1) ? null : String.valueOf(d.getParentid());
                departmentService.upsert(PLATFORM_TYPE, String.valueOf(d.getId()), d.getName(), parentPid, null);
            }

            int newCount = 0;
            int updateCount = 0;
            int totalCount = 0;
            List<String> errors = new ArrayList<>();

            List<Department> departments = getDepartmentList(accessToken);
            for (Department dept : departments) {
                try {
                    List<WeChatUser> users = getDepartmentUsers(accessToken, dept.getId());
                    totalCount += users.size();

                    for (WeChatUser user : users) {
                        try {
                            Employee existingEmployee = employeeServiceProvider.getObject().getByPlatformUserId(user.getUserid(), PLATFORM_TYPE);

                            if (existingEmployee == null) {
                                Employee candidate = convertToEmployee(user, dept);
                                // 按工号回填已有员工
                                if (candidate.getEmployeeId() != null) {
                                    Employee byEmpId = employeeServiceProvider.getObject().getByEmployeeId(candidate.getEmployeeId());
                                    if (byEmpId != null) {
                                        Employee update = candidate;
                                        employeeServiceProvider.getObject().updateEmployee(byEmpId.getId(), update);
                                        updateCount++;
                                        log.debug("回填并更新企微员工(通过工号匹配): {}", candidate.getName());
                                        continue;
                                    }
                                }
                                employeeServiceProvider.getObject().createEmployee(candidate);
                                // 自动创建并绑定后台用户账号
                                userBindingServiceProvider.getObject().ensureUserForEmployee(candidate);
                                newCount++;
                                log.debug("新增企微员工: {}", user.getName());
                            } else {
                                Employee updateInfo = convertToEmployee(user, dept);
                                employeeServiceProvider.getObject().updateEmployee(existingEmployee.getId(), updateInfo);
                                userBindingServiceProvider.getObject().ensureUserForEmployee(updateInfo);
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
    public java.util.List<Employee> fetchAllEmployees() {
        java.util.Map<String, Employee> seen = new java.util.LinkedHashMap<>();
        try {
            if (!integrationConfigService.isPlatformEnabled(PLATFORM_TYPE)) {
                log.warn("企业微信未启用或未配置，fetch跳过");
                return new java.util.ArrayList<>();
            }
            String accessToken = getAccessToken();
            if (accessToken == null) return new java.util.ArrayList<>();
            java.util.List<Department> departments = getDepartmentList(accessToken);
            java.util.Map<String, String> deptNameMap = new java.util.HashMap<>();
            for (Department d : departments) deptNameMap.put(d.getId(), d.getName());
            // 拉取所有部门成员（会有重复），但按用户聚合其部门IDs
            java.util.Map<String, java.util.LinkedHashSet<String>> userDeptNames = new java.util.HashMap<>();
            for (Department dept : departments) {
                java.util.List<WeChatUser> users = getDepartmentUsers(accessToken, dept.getId());
                for (WeChatUser user : users) {
                    String key = user.getUserid();
                    userDeptNames.computeIfAbsent(key, k -> new java.util.LinkedHashSet<>());
                    if (user.getDepartment() != null) {
                        for (Integer depId : user.getDepartment()) {
                            String name = deptNameMap.get(String.valueOf(depId));
                            if (name != null) userDeptNames.get(key).add(name);
                        }
                    }
                    if (!seen.containsKey(key)) {
                        Employee e = convertToEmployee(user, null);
                        e.setEmployeeId(null); // 预览不设置工号
                        seen.put(key, e);
                    }
                }
            }
            // 将聚合的部门名称串接到 Employee.department 以便前端拆分
            for (java.util.Map.Entry<String, Employee> entry : seen.entrySet()) {
                java.util.LinkedHashSet<String> names = userDeptNames.get(entry.getKey());
                if (names != null && !names.isEmpty()) {
                    entry.getValue().setDepartment(String.join(",", names));
                }
            }
        } catch (Exception e) {
            log.error("企微fetchAllEmployees异常", e);
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
            java.util.List<WeChatDepartment> rawDepts = getWeChatDepartmentRawList(accessToken);
            java.util.Map<Integer, com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto> map = new java.util.LinkedHashMap<>();
            for (WeChatDepartment d : rawDepts) {
                com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto node = new com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto();
                node.setPlatformDeptId(String.valueOf(d.getId()));
                node.setName(d.getName());
                node.setParentPlatformDeptId(d.getParentid() <= 1 ? null : String.valueOf(d.getParentid()));
                map.put(d.getId(), node);
            }
            // build tree
            for (WeChatDepartment d : rawDepts) {
                com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto node = map.get(d.getId());
                if (d.getParentid() <= 1) {
                    roots.add(node);
                } else {
                    com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto parent = map.get(d.getParentid());
                    if (parent != null) parent.getChildren().add(node);
                }
            }
            // attach members per dept
            for (WeChatDepartment d : rawDepts) {
                com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto node = map.get(d.getId());
                java.util.List<WeChatUser> users = getDepartmentUsers(accessToken, String.valueOf(d.getId()));
                if (users != null) {
                    for (WeChatUser u : users) {
                        com.yiyundao.compensation.interfaces.dto.org.OrgMemberPreviewDto m = new com.yiyundao.compensation.interfaces.dto.org.OrgMemberPreviewDto();
                        m.setPlatformUserId(u.getUserid());
                        m.setName(u.getName());
                        m.setPhone(u.getMobile());
                        m.setEmail(u.getEmail());
                        m.setPosition(u.getPosition());
                        node.getMembers().add(m);
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取企微部门树异常", e);
        }
        return roots;
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
            if (cfg == null || cfg.getCorpId() == null || cfg.getCorpId().isBlank() 
                || cfg.getCorpSecret() == null || cfg.getCorpSecret().isBlank()) {
                log.error("企业微信配置不存在或未完整配置，请先在数据库中配置");
                return null;
            }
            
            String id = cfg.getCorpId();
            String secret = cfg.getCorpSecret();
            
            // 检查是否是占位符
            if (id.toLowerCase().startsWith("your_") || secret.toLowerCase().startsWith("your_")) {
                log.warn("企业微信配置使用了占位符，请配置真实的 corpId 和 corpSecret");
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
        if (cfg != null && cfg.getAgentId() != null && !cfg.getAgentId().isBlank()) {
            return cfg.getAgentId();
        }
        log.warn("企业微信 agentId 未配置");
        return null;
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

    private List<WeChatDepartment> getWeChatDepartmentRawList(String accessToken) {
        try {
            String url = API_BASE_URL + "/department/list?access_token=" + accessToken;
            WeChatDepartmentListResponse resp = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(WeChatDepartmentListResponse.class)
                    .block();
            if (resp != null && resp.getErrcode() == 0 && resp.getDepartment() != null) {
                return resp.getDepartment();
            }
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
    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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
