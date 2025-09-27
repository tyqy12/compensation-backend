package com.yiyundao.compensation.adapter.impl;

import com.yiyundao.compensation.adapter.OrganizationAdapter;
import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 企业微信组织同步适配器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatOrganizationAdapter implements OrganizationAdapter {

    private final EmployeeService employeeService;
    private final WebClient webClient;

    @Value("${wechat.corp-id}")
    private String corpId;

    @Value("${wechat.corp-secret}")
    private String corpSecret;

    @Value("${wechat.agent-id}")
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
            // 1. 获取访问令牌
            String accessToken = getAccessToken();
            if (accessToken == null) {
                return OrganizationSyncResult.failure(PLATFORM_TYPE, "获取访问令牌失败", null);
            }

            // 2. 获取部门列表
            List<Department> departments = getDepartmentList(accessToken);

            // 3. 同步员工信息
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
                            Employee existingEmployee = employeeService.getByPlatformUserId(user.getUserId(), PLATFORM_TYPE);

                            if (existingEmployee == null) {
                                // 新增员工
                                Employee newEmployee = convertToEmployee(user, dept);
                                employeeService.createEmployee(newEmployee);
                                newCount++;
                                log.debug("新增企微员工: {}", user.getName());

                            } else {
                                // 更新员工信息
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

            // 调用企微API获取用户详情
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
        // TODO: 实现管理员权限检查
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

            // 构造消息
            String messageBody = String.format(
                    "{\"touser\":\"%s\",\"msgtype\":\"text\",\"agentid\":%s,\"text\":{\"content\":\"%s\"}}",
                    platformUserId, agentId, message);

            // 发送消息
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

    /**
     * 获取访问令牌
     */
    private String getAccessToken() {
        try {
            String url = API_BASE_URL + "/gettoken?corpid=" + corpId + "&corpsecret=" + corpSecret;

            WeChatTokenResponse response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(WeChatTokenResponse.class)
                    .block();

            if (response != null && response.getErrcode() == 0) {
                return response.getAccessToken();
            } else {
                log.error("获取企微访问令牌失败: {}", response != null ? response.getErrmsg() : "null response");
                return null;
            }

        } catch (Exception e) {
            log.error("获取企微访问令牌异常", e);
            return null;
        }
    }

    /**
     * 获取部门列表
     */
    private List<Department> getDepartmentList(String accessToken) {
        // TODO: 实现获取部门列表
        List<Department> departments = new ArrayList<>();
        departments.add(new Department("1", "根部门"));
        return departments;
    }

    /**
     * 获取部门用户
     */
    private List<WeChatUser> getDepartmentUsers(String accessToken, String departmentId) {
        // TODO: 实现获取部门用户
        return new ArrayList<>();
    }

    /**
     * 转换为Employee对象
     */
    private Employee convertToEmployee(WeChatUser user, Department dept) {
        Employee employee = new Employee();
        employee.setEmployeeId(user.getUserId());
        employee.setName(user.getName());
        employee.setPhone(user.getMobile());
        employee.setEmail(user.getEmail());
        employee.setPlatformUserId(user.getUserId());
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
        private String accessToken;

        // getters and setters
        public int getErrcode() { return errcode; }
        public void setErrcode(int errcode) { this.errcode = errcode; }
        public String getErrmsg() { return errmsg; }
        public void setErrmsg(String errmsg) { this.errmsg = errmsg; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    }

    private static class WeChatUserResponse {
        private int errcode;
        private String errmsg;
        private WeChatUser user;

        // getters and setters
        public int getErrcode() { return errcode; }
        public void setErrcode(int errcode) { this.errcode = errcode; }
        public String getErrmsg() { return errmsg; }
        public void setErrmsg(String errmsg) { this.errmsg = errmsg; }
        public WeChatUser getUser() { return user; }
        public void setUser(WeChatUser user) { this.user = user; }
    }

    private static class WeChatUser {
        private String userId;
        private String name;
        private String mobile;
        private String email;
        private String position;

        // getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }
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
