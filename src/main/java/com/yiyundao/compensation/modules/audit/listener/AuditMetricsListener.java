package com.yiyundao.compensation.modules.audit.listener;

import com.yiyundao.compensation.modules.audit.event.AuditLogSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审计指标统计监听器
 * <p>
 * 收集审计日志指标，用于监控和统计。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditMetricsListener {

    /**
     * 今日操作计数 (operation -> count)
     */
    private final Map<String, Integer> todayOperationCount = new ConcurrentHashMap<>();

    /**
     * 今日业务类型计数 (businessType -> count)
     */
    private final Map<String, Integer> todayBusinessTypeCount = new ConcurrentHashMap<>();

    /**
     * 今日成功/失败计数
     */
    private final Map<String, Integer> todayResultCount = new ConcurrentHashMap<>();

    /**
     * 最后统计时间
     */
    private LocalDateTime lastStatsTime = LocalDateTime.now();

    /**
     * 处理审计日志保存事件
     */
    @Async
    @EventListener
    public void onAuditLogSaved(AuditLogSavedEvent event) {
        try {
            // 检查是否需要重置统计（跨天）
            checkAndResetStats();

            // 统计操作类型
            String operation = event.getOperation();
            if (operation != null) {
                todayOperationCount.merge(operation, 1, (oldVal, newVal) -> oldVal + newVal);
            }

            // 统计业务类型
            String businessType = event.getBusinessType();
            if (businessType != null) {
                todayBusinessTypeCount.merge(businessType, 1, (oldVal, newVal) -> oldVal + newVal);
            }

            // 统计成功/失败
            String result = event.isSuccess() ? "success" : "failed";
            todayResultCount.merge(result, 1, (oldVal, newVal) -> oldVal + newVal);

        } catch (Exception e) {
            log.error("审计指标统计失败", e);
        }
    }

    /**
     * 检查并重置统计（跨天时重置）
     */
    private void checkAndResetStats() {
        LocalDateTime now = LocalDateTime.now();
        if (now.toLocalDate().isAfter(lastStatsTime.toLocalDate())) {
            // 新的一天，重置统计
            todayOperationCount.clear();
            todayBusinessTypeCount.clear();
            todayResultCount.clear();
            lastStatsTime = now;
            log.info("审计统计已重置（新的一天）");
        }
    }

    /**
     * 获取操作类型统计
     */
    public Map<String, Integer> getOperationStats() {
        return Map.copyOf(todayOperationCount);
    }

    /**
     * 获取业务类型统计
     */
    public Map<String, Integer> getBusinessTypeStats() {
        return Map.copyOf(todayBusinessTypeCount);
    }

    /**
     * 获取结果统计
     */
    public Map<String, Integer> getResultStats() {
        return Map.copyOf(todayResultCount);
    }

    /**
     * 获取今日统计摘要
     */
    public Map<String, Object> getTodaySummary() {
        Map<String, Object> summary = new ConcurrentHashMap<>();
        summary.put("operationStats", getOperationStats());
        summary.put("businessTypeStats", getBusinessTypeStats());
        summary.put("resultStats", getResultStats());

        int total = todayResultCount.getOrDefault("success", 0) +
                todayResultCount.getOrDefault("failed", 0);
        int success = todayResultCount.getOrDefault("success", 0);

        summary.put("totalCount", total);
        summary.put("successCount", success);
        summary.put("failedCount", todayResultCount.getOrDefault("failed", 0));
        summary.put("successRate", total > 0 ?
                String.format("%.2f%%", (double) success / total * 100) : "0%");

        return summary;
    }
}
