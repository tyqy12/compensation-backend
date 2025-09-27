package com.yiyundao.compensation.service;

import com.yiyundao.compensation.interfaces.adapter.OrganizationAdapter;
import com.yiyundao.compensation.dto.OrganizationSyncResult;
import lombok.extern.slf4j.Slf4j;
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

    @Autowired
    public OrganizationSyncService(List<OrganizationAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(
                    OrganizationAdapter::getPlatformType,
                    adapter -> adapter
                ));
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
        log.info("开始同步所有平台组织架构");

        return adapters.values().stream()
                .map(adapter -> {
                    try {
                        return adapter.syncOrganization();
                    } catch (Exception e) {
                        log.error("平台同步失败: {}", adapter.getPlatformType(), e);
                        return OrganizationSyncResult.failure(
                            adapter.getPlatformType(),
                            "同步失败: " + e.getMessage(),
                            null
                        );
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 定时同步任务 (每小时执行一次)
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledSync() {
        log.info("开始定时组织架构同步");

        List<OrganizationSyncResult> results = syncAllPlatforms();

        // 记录同步结果
        for (OrganizationSyncResult result : results) {
            if (result.isSuccess()) {
                log.info("平台{}同步成功: 总计{}人, 新增{}人, 更新{}人",
                        result.getPlatformType(),
                        result.getTotalEmployees(),
                        result.getNewEmployees(),
                        result.getUpdatedEmployees());
            } else {
                log.error("平台{}同步失败: {}", result.getPlatformType(), result.getMessage());
            }
        }
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
     * 发送平台通知
     */
    public void sendNotification(String platformType, String userId, String message) {
        OrganizationAdapter adapter = adapters.get(platformType);
        if (adapter != null) {
            try {
                adapter.sendApprovalNotification(userId, message);
            } catch (Exception e) {
                log.error("发送平台通知失败: platform={}, userId={}", platformType, userId, e);
            }
        }
    }
}
