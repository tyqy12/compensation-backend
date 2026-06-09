package com.yiyundao.compensation.interfaces.adapter.impl;

import com.yiyundao.compensation.common.utils.SecretLogSanitizer;
import com.yiyundao.compensation.interfaces.adapter.OrganizationAdapter;
import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeVO;
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
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    private static final List<String> EMPLOYEE_ID_EXTATTR_KEYS = List.of(
            "员工工号", "工号", "employee_no", "employeeno", "employeeid", "employee_id"
    );
    private static final Set<Integer> INACTIVE_STATUS_CODES = Set.of(2, 4, 5);

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
                    List<WeChatUser> users = getDepartmentUsers(accessToken, dept.getId(), false);
                    totalCount += users.size();

                    for (WeChatUser user : users) {
                        try {
                            Employee existingEmployee = employeeServiceProvider.getObject()
                                    .getByProviderAndSubjectId(PLATFORM_TYPE, user.getUserid());

                            if (existingEmployee == null) {
                                Employee candidate = convertToEmployee(user, dept);
                                // 按工号回填已有员工
                                String extEmployeeId = resolveEmployeeIdFromExtattr(user.getExtattr());
                                if (StringUtils.hasText(extEmployeeId)) {
                                    Employee byEmpId = employeeServiceProvider.getObject().getByEmployeeId(extEmployeeId);
                                    if (byEmpId != null) {
                                        Employee update = candidate;
                                        EmployeeVO updatedVo = employeeServiceProvider.getObject().updateEmployee(byEmpId.getId(), update);
                                        Employee persisted = resolvePersistedEmployee(byEmpId, updatedVo != null ? updatedVo.getId() : null);
                                        userBindingServiceProvider.getObject().ensureUserForEmployee(persisted);
                                        updateCount++;
                                        log.debug("回填并更新企微员工(通过工号匹配): {}", candidate.getName());
                                        continue;
                                    }
                                }
                                EmployeeVO createdVo = employeeServiceProvider.getObject().createEmployee(candidate);
                                Employee persisted = resolvePersistedEmployee(candidate, createdVo != null ? createdVo.getId() : null);
                                // 自动创建并绑定后台用户账号
                                userBindingServiceProvider.getObject().ensureUserForEmployee(persisted);
                                newCount++;
                                log.debug("新增企微员工: {}", user.getName());
                            } else {
                                Employee updateInfo = convertToEmployee(user, dept);
                                EmployeeVO updatedVo = employeeServiceProvider.getObject().updateEmployee(existingEmployee.getId(), updateInfo);
                                Employee persisted = resolvePersistedEmployee(
                                        existingEmployee,
                                        updatedVo != null ? updatedVo.getId() : existingEmployee.getId());
                                userBindingServiceProvider.getObject().ensureUserForEmployee(persisted);
                                updateCount++;
                                log.debug("更新企微员工: {}", user.getName());
                            }

                        } catch (Exception e) {
                            String error = "同步员工失败: " + user.getName() + " - " + SecretLogSanitizer.sanitize(e);
                            errors.add(error);
                            log.error("同步企微员工失败: {}, error={}", user.getName(), SecretLogSanitizer.sanitize(e));
                        }
                    }

                } catch (Exception e) {
                    String error = "同步部门失败: " + dept.getName() + " - " + SecretLogSanitizer.sanitize(e);
                    errors.add(error);
                    log.error("同步企微部门失败: {}, error={}", dept.getName(), SecretLogSanitizer.sanitize(e));
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
            log.error("企业微信组织架构同步异常: {}", SecretLogSanitizer.sanitize(e));
            return OrganizationSyncResult.failure(PLATFORM_TYPE, "同步异常: " + SecretLogSanitizer.sanitize(e), null);
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
                java.util.List<WeChatUser> users = getDepartmentUsers(accessToken, dept.getId(), false);
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
            log.error("企微fetchAllEmployees异常: {}", SecretLogSanitizer.sanitize(e));
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
                java.util.List<WeChatUser> users = getDepartmentUsers(accessToken, String.valueOf(d.getId()), false);
                if (users != null) {
                    for (WeChatUser u : users) {
                        com.yiyundao.compensation.interfaces.dto.org.OrgMemberPreviewDto m = new com.yiyundao.compensation.interfaces.dto.org.OrgMemberPreviewDto();
                        m.setProvider(PLATFORM_TYPE);
                        m.setSubjectId(u.getUserid());
                        m.setName(u.getName());
                        m.setPhone(resolvePhone(u));
                        m.setEmail(trimToNull(u.getEmail()));
                        m.setPosition(resolvePosition(u));
                        node.getMembers().add(m);
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取企微部门树异常: {}", SecretLogSanitizer.sanitize(e));
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
                return convertToEmployee(response, null);
            }

        } catch (Exception e) {
            log.error("获取企微用户信息失败: {}, error={}", platformUserId, SecretLogSanitizer.sanitize(e));
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

            List<WeChatUser> users = getDepartmentUsers(accessToken, departmentId, false);
            List<Employee> employees = new ArrayList<>();

            for (WeChatUser user : users) {
                Employee employee = convertToEmployee(user, null);
                employees.add(employee);
            }

            return employees;

        } catch (Exception e) {
            log.error("获取企微部门员工失败: {}, error={}", departmentId, SecretLogSanitizer.sanitize(e));
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
            log.error("发送企微审批通知失败: {}", SecretLogSanitizer.sanitize(e));
        }
    }

    @Override
    public boolean checkConnection() {
        try {
            String accessToken = getAccessToken();
            return accessToken != null;
        } catch (Exception e) {
            log.error("检查企微连接失败: {}", SecretLogSanitizer.sanitize(e));
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
                log.error("获取企微访问令牌失败: {}", response != null ? SecretLogSanitizer.sanitize(response.getErrmsg()) : "null response");
                return null;
            }

        } catch (Exception e) {
            log.error("获取企微访问令牌异常: {}", SecretLogSanitizer.sanitize(e));
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
            log.error("获取企微部门列表异常: {}", SecretLogSanitizer.sanitize(e));
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
            log.error("获取企微部门列表异常: {}", SecretLogSanitizer.sanitize(e));
        }
        return new ArrayList<>();
    }

    private List<WeChatUser> getDepartmentUsers(String accessToken, String departmentId, boolean fetchChild) {
        try {
            String url = API_BASE_URL + "/user/list?access_token=" + accessToken + "&department_id=" + departmentId
                    + "&fetch_child=" + (fetchChild ? 1 : 0);
            WeChatUserListResponse resp = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(WeChatUserListResponse.class)
                    .block();
            if (resp != null && resp.getErrcode() == 0 && resp.getUserlist() != null) {
                return resp.getUserlist();
            }
            log.warn("获取企微部门用户失败: departmentId={}, fetchChild={}, err={}",
                    departmentId, fetchChild, resp != null ? resp.getErrmsg() : "null response");
        } catch (Exception e) {
            log.error("获取企微部门用户异常: departmentId={}, fetchChild={}, error={}", departmentId, fetchChild, SecretLogSanitizer.sanitize(e));
        }
        return new ArrayList<>();
    }

    private Employee convertToEmployee(WeChatUser user, Department dept) {
        Employee employee = new Employee();
        employee.setEmployeeId(resolveEmployeeId(user));
        employee.setName(trimToNull(user.getName()));
        employee.setPhone(resolvePhone(user));
        employee.setEmail(trimToNull(user.getEmail()));
        employee.setSubjectId(trimToNull(user.getUserid()));
        employee.setProvider(PLATFORM_TYPE);
        employee.setDepartment(dept != null ? dept.getName() : null);
        employee.setPosition(resolvePosition(user));
        employee.setStatus(resolveStatus(user));
        employee.setOffline(false);
        return employee;
    }

    private String resolveEmployeeId(WeChatUser user) {
        String fromExt = resolveEmployeeIdFromExtattr(user.getExtattr());
        if (StringUtils.hasText(fromExt)) {
            return fromExt;
        }
        return trimToNull(user.getUserid());
    }

    private String resolveEmployeeIdFromExtattr(WeChatExtAttr extattr) {
        if (extattr == null || extattr.getAttrs() == null || extattr.getAttrs().isEmpty()) {
            return null;
        }
        for (String key : EMPLOYEE_ID_EXTATTR_KEYS) {
            String value = findExtattrValue(extattr, key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String findExtattrValue(WeChatExtAttr extattr, String expectedKey) {
        String normalizedExpected = normalizeExtattrKey(expectedKey);
        for (WeChatExtAttrItem item : extattr.getAttrs()) {
            if (item == null || !StringUtils.hasText(item.getName())) {
                continue;
            }
            if (!normalizeExtattrKey(item.getName()).equals(normalizedExpected)) {
                continue;
            }
            if (item.getText() != null && StringUtils.hasText(item.getText().getValue())) {
                return item.getText().getValue();
            }
            if (item.getWeb() != null && StringUtils.hasText(item.getWeb().getTitle())) {
                return item.getWeb().getTitle();
            }
        }
        return null;
    }

    private String normalizeExtattrKey(String key) {
        if (key == null) {
            return "";
        }
        return key.replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String resolvePhone(WeChatUser user) {
        if (StringUtils.hasText(user.getMobile())) {
            return user.getMobile().trim();
        }
        return trimToNull(user.getTelephone());
    }

    private String resolvePosition(WeChatUser user) {
        if (StringUtils.hasText(user.getPosition())) {
            return user.getPosition().trim();
        }
        return trimToNull(user.getExternal_position());
    }

    private String resolveStatus(WeChatUser user) {
        if (user.getEnable() != null && user.getEnable() == 0) {
            return "inactive";
        }
        if (user.getStatus() != null && INACTIVE_STATUS_CODES.contains(user.getStatus())) {
            return "inactive";
        }
        return "active";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Employee resolvePersistedEmployee(Employee fallback, Long id) {
        Long employeeId = id != null ? id : (fallback != null ? fallback.getId() : null);
        if (employeeId != null) {
            Employee persisted = employeeServiceProvider.getObject().getById(employeeId);
            if (persisted != null) {
                return persisted;
            }
        }
        return fallback;
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
    private static class WeChatUserResponse extends WeChatUser {
        private int errcode;
        private String errmsg;

        public int getErrcode() { return errcode; }
        public void setErrcode(int errcode) { this.errcode = errcode; }
        public String getErrmsg() { return errmsg; }
        public void setErrmsg(String errmsg) { this.errmsg = errmsg; }
    }

    @SuppressWarnings("unused")
    private static class WeChatUser {
        private String userid;
        private String name;
        private String mobile;
        private String email;
        private String telephone;
        private String position;
        private String external_position;
        private List<Integer> department;
        private String alias;
        private Integer enable;
        private Integer status;
        private Integer hide_mobile;
        private Integer isleader;
        private List<Integer> is_leader_in_dept;
        private List<String> direct_leader;
        private WeChatExtAttr extattr;
        private Object external_profile;

        public String getUserid() { return userid; }
        public void setUserid(String userid) { this.userid = userid; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getTelephone() { return telephone; }
        public void setTelephone(String telephone) { this.telephone = telephone; }
        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }
        public String getExternal_position() { return external_position; }
        public void setExternal_position(String external_position) { this.external_position = external_position; }
        public List<Integer> getDepartment() { return department; }
        public void setDepartment(List<Integer> department) { this.department = department; }
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
        public Integer getEnable() { return enable; }
        public void setEnable(Integer enable) { this.enable = enable; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public Integer getHide_mobile() { return hide_mobile; }
        public void setHide_mobile(Integer hide_mobile) { this.hide_mobile = hide_mobile; }
        public Integer getIsleader() { return isleader; }
        public void setIsleader(Integer isleader) { this.isleader = isleader; }
        public List<Integer> getIs_leader_in_dept() { return is_leader_in_dept; }
        public void setIs_leader_in_dept(List<Integer> is_leader_in_dept) { this.is_leader_in_dept = is_leader_in_dept; }
        public List<String> getDirect_leader() { return direct_leader; }
        public void setDirect_leader(List<String> direct_leader) { this.direct_leader = direct_leader; }
        public WeChatExtAttr getExtattr() { return extattr; }
        public void setExtattr(WeChatExtAttr extattr) { this.extattr = extattr; }
        public Object getExternal_profile() { return external_profile; }
        public void setExternal_profile(Object external_profile) { this.external_profile = external_profile; }
    }

    @SuppressWarnings("unused")
    private static class WeChatExtAttr {
        private List<WeChatExtAttrItem> attrs;
        public List<WeChatExtAttrItem> getAttrs() { return attrs; }
        public void setAttrs(List<WeChatExtAttrItem> attrs) { this.attrs = attrs; }
    }

    @SuppressWarnings("unused")
    private static class WeChatExtAttrItem {
        private Integer type;
        private String name;
        private WeChatExtAttrText text;
        private WeChatExtAttrWeb web;
        public Integer getType() { return type; }
        public void setType(Integer type) { this.type = type; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public WeChatExtAttrText getText() { return text; }
        public void setText(WeChatExtAttrText text) { this.text = text; }
        public WeChatExtAttrWeb getWeb() { return web; }
        public void setWeb(WeChatExtAttrWeb web) { this.web = web; }
    }

    @SuppressWarnings("unused")
    private static class WeChatExtAttrText {
        private String value;
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    @SuppressWarnings("unused")
    private static class WeChatExtAttrWeb {
        private String title;
        private String url;
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
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
