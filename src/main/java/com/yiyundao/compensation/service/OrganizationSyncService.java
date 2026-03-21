package com.yiyundao.compensation.service;

import com.yiyundao.compensation.interfaces.adapter.OrganizationAdapter;
import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 组织同步服务
 * 负责协调各平台的组织架构同步
 */
@Slf4j
@Service
public class OrganizationSyncService {

    private final Map<String, OrganizationAdapter> adapters;
    private final NotificationService notificationService;
    private final com.yiyundao.compensation.modules.system.service.IntegrationConfigService integrationConfigService;
    private final com.yiyundao.compensation.modules.employee.service.EmployeeService employeeService;
    private final com.yiyundao.compensation.modules.user.service.UserBindingService userBindingService;

    @Value("${notification.retry.max:3}")
    private int notifyRetryMax;

    @Value("${notification.retry.backoff-ms:500}")
    private long notifyBackoffMs;

    @Value("${org.sync.scheduled.enabled:false}")
    private boolean scheduledEnabled;

    @Autowired
    public OrganizationSyncService(List<OrganizationAdapter> adapterList,
                                   NotificationService notificationService,
                                   com.yiyundao.compensation.modules.system.service.IntegrationConfigService integrationConfigService,
                                   com.yiyundao.compensation.modules.employee.service.EmployeeService employeeService,
                                   com.yiyundao.compensation.modules.user.service.UserBindingService userBindingService) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(
                    OrganizationAdapter::getPlatformType,
                    adapter -> adapter
                ));
        this.notificationService = notificationService;
        this.integrationConfigService = integrationConfigService;
        this.employeeService = employeeService;
        this.userBindingService = userBindingService;
        log.info("已注册组织同步适配器: {}", adapters.keySet());
    }

    /**
     * 手动触发组织同步
     */
    public OrganizationSyncResult syncPlatform(String platformType) {
        log.info("手动同步平台组织架构: {}", platformType);

        OrganizationAdapter adapter = adapters.get(platformType);
        if (adapter == null) {
            log.error("未找到平台适配器: {}", platformType);
            return OrganizationSyncResult.failure(platformType, "未找到平台适配器", null);
        }

        // 平台未启用或未配置，则跳过
        if (!integrationConfigService.isPlatformEnabled(platformType)) {
            log.warn("平台未启用，同步跳过: {}", platformType);
            return OrganizationSyncResult.failure(platformType, "平台未启用或未配置，跳过", null);
        }

        try {
            return adapter.syncOrganization();
        } catch (Exception e) {
            log.error("平台组织同步异常: {}", platformType, e);
            return OrganizationSyncResult.failure(platformType, "同步异常: " + e.getMessage(), null);
        }
    }

    /**
     * 同步所有平台
     */
    public List<OrganizationSyncResult> syncAllPlatforms() {
        // 已取消自动同步
        return java.util.Collections.emptyList();
    }

    /**
     * 定时同步任务 (每小时执行一次)
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledSync() {
        // 已取消自动同步
        return;
    }

    /**
     * 检查平台连接状态
     */
    public boolean checkPlatformConnection(String platformType) {
        OrganizationAdapter adapter = adapters.get(platformType);
        if (adapter == null) {
            return false;
        }

        try {
            return adapter.checkConnection();
        } catch (Exception e) {
            log.error("检查平台连接失败: {}", platformType, e);
            return false;
        }
    }

    /**
     * 获取支持的平台列表
     */
    public List<String> getSupportedPlatforms() {
        return adapters.keySet().stream().collect(Collectors.toList());
    }

    /**
     * 手动获取平台员工预览（不落库）
     */
    public java.util.List<com.yiyundao.compensation.modules.employee.entity.Employee> fetchPreview(String platformType) {
        OrganizationAdapter adapter = adapters.get(platformType);
        if (adapter == null) return java.util.Collections.emptyList();
        if (!integrationConfigService.isPlatformEnabled(platformType)) return java.util.Collections.emptyList();
        return adapter.fetchAllEmployees();
    }

    public java.util.List<com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto> fetchDepartmentTree(String platformType) {
        OrganizationAdapter adapter = adapters.get(platformType);
        if (adapter == null) return java.util.Collections.emptyList();
        if (!integrationConfigService.isPlatformEnabled(platformType)) return java.util.Collections.emptyList();
        return adapter.fetchDepartmentTree();
    }

    /**
     * 手动导入单个员工（带用户名偏好）
     */
    public com.yiyundao.compensation.modules.employee.entity.Employee importOne(com.yiyundao.compensation.modules.employee.entity.Employee employee, String preferredUsername) {
        com.yiyundao.compensation.modules.employee.entity.Employee target = null;
        if (employee.getSubjectId() != null && employee.getProvider() != null) {
            try {
                com.yiyundao.compensation.modules.employee.entity.Employee exist =
                        employeeService.getByProviderAndSubjectId(employee.getProvider(), employee.getSubjectId());
                if (exist != null) {
                    // 更新已有记录
                    com.yiyundao.compensation.modules.employee.entity.Employee update = new com.yiyundao.compensation.modules.employee.entity.Employee();
                    update.setName(employee.getName());
                    update.setPhone(employee.getPhone());
                    update.setEmail(employee.getEmail());
                    update.setDepartment(employee.getDepartment());
                    update.setPosition(employee.getPosition());
                    update.setEmploymentType(employee.getEmploymentType());
                    update.setStatus(employee.getStatus());
                    update.setOffline(employee.getOffline());
                    update.setManagerId(employee.getManagerId());
                    update.setBankAccount(employee.getBankAccount());
                    update.setBankName(employee.getBankName());
                    update.setHireDate(employee.getHireDate());
                    EmployeeVO updatedVo = employeeService.updateEmployee(exist.getId(), update);
                    target = employeeService.getById(updatedVo.getId());
                }
            } catch (Exception ignored) {}
        }
        if (target == null) {
            EmployeeVO createdVo = employeeService.createEmployee(employee);
            target = employeeService.getById(createdVo.getId());
        }
        userBindingService.ensureUserForEmployee(target, preferredUsername);
        return target;
    }

    /**
     * 发送平台通知
     */
    public void sendNotification(String provider, String subjectId, String message) {
        OrganizationAdapter adapter = adapters.get(provider);
        if (adapter == null) {
            log.warn("未找到平台适配器: {}，使用回退通知", provider);
            notificationService.sendFallbackNotification(provider, subjectId, message);
            return;
        }
        Exception last = null;
        for (int i = 1; i <= Math.max(1, notifyRetryMax); i++) {
            try {
                adapter.sendApprovalNotification(subjectId, message);
                if (i > 1) {
                    log.info("通知重试第{}次成功: provider={}, subjectId={}", i - 1, provider, subjectId);
                }
                return;
            } catch (Exception e) {
                last = e;
                log.warn("发送平台通知失败(第{}次): provider={}, subjectId={}, err={}", i, provider, subjectId, e.getMessage());
                try { Thread.sleep(Math.max(0, notifyBackoffMs)); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        log.error("通知重试耗尽，使用回退策略: provider={}, subjectId={}", provider, subjectId, last);
        notificationService.sendFallbackNotification(provider, subjectId, message);
    }
}
