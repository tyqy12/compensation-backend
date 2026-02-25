package com.yiyundao.compensation.modules.audit.listener;

import com.yiyundao.compensation.modules.audit.event.AuditLogSavedEvent;
import com.yiyundao.compensation.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 安全告警事件监听器
 * <p>
 * 监听高危操作，当检测到敏感操作时触发安全告警。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAlertListener {

    private final NotificationService notificationService;

    /**
     * 高危操作关键词（不区分大小写）
     */
    private static final Set<String> HIGH_RISK_OPERATIONS = Set.of(
            "删除", "DELETE", "remove",
            "重置", "RESET", "reset",
            "修改密码", "CHANGE_PASSWORD", "change_password",
            "锁定", "LOCK", "lock",
            "解绑", "UNBIND", "unbind",
            "导出", "EXPORT", "export",
            "批量删除", "BATCH_DELETE", "batch_delete",
            "清空", "CLEAR", "clear"
    );

    /**
     * 敏感配置关键词
     */
    private static final Set<String> SENSITIVE_CONFIGS = Set.of(
            "密码", "PASSWORD", "password",
            "密钥", "SECRET", "secret",
            "令牌", "TOKEN", "token",
            "KEY", "key"
    );

    /**
     * 告警操作白名单（这些操作不触发告警）
     */
    private static final Set<String> WHITELIST_OPERATIONS = Set.of(
            "用户登录", "OAuth登录", "企业微信登录",
            "dingtalk登录", "飞书登录"
    );

    /**
     * 处理审计日志保存事件
     */
    @Async
    @EventListener
    public void onAuditLogSaved(AuditLogSavedEvent event) {
        // 只处理认证相关的操作
        if (!"AUTH".equals(event.getBusinessType())) {
            return;
        }

        // 白名单中的操作不触发告警
        if (isWhitelisted(event.getOperation())) {
            return;
        }

        // 检查是否是高危操作
        if (isHighRiskOperation(event)) {
            triggerSecurityAlert(event, "高危操作");
        }
    }

    /**
     * 判断操作是否在白名单中
     */
    private boolean isWhitelisted(String operation) {
        if (operation == null) {
            return false;
        }
        return WHITELIST_OPERATIONS.stream().anyMatch(whitelist ->
                operation.contains(whitelist) || whitelist.contains(operation)
        );
    }

    /**
     * 判断是否是高危操作
     */
    private boolean isHighRiskOperation(AuditLogSavedEvent event) {
        String operation = event.getOperation();
        if (operation == null) {
            return false;
        }

        // 检查操作是否包含高危关键词
        boolean isHighRisk = HIGH_RISK_OPERATIONS.stream()
                .anyMatch(keyword -> operation.toLowerCase().contains(keyword.toLowerCase()));

        if (isHighRisk) {
            log.debug("检测到高危操作: operation={}", operation);
            return true;
        }

        // 如果操作失败且包含敏感关键词，也触发告警
        if (event.hasError()) {
            boolean hasSensitive = SENSITIVE_CONFIGS.stream()
                    .anyMatch(keyword -> operation.toLowerCase().contains(keyword.toLowerCase()));
            if (hasSensitive) {
                log.debug("检测到敏感操作失败: operation={}, error={}", operation, event.getAuditLog().getErrorMsg());
                return true;
            }
        }

        return false;
    }

    /**
     * 触发安全告警
     */
    private void triggerSecurityAlert(AuditLogSavedEvent event, String alertType) {
        try {
            String content = String.format(
                    "检测到%s，请关注！\n\n" +
                            "操作：%s\n" +
                            "用户：%s\n" +
                            "时间：%s\n" +
                            "结果：%s\n" +
                            "IP地址：%s\n",
                    alertType,
                    event.getOperation(),
                    event.getUsername() != null ? event.getUsername() : "未知",
                    LocalDateTime.now(),
                    event.isSuccess() ? "成功" : "失败",
                    event.getAuditLog().getRequestIp()
            );

            if (event.hasError()) {
                content += "错误信息：" + event.getAuditLog().getErrorMsg();
            }

            notificationService.sendSystemAlert(
                    alertType,
                    content,
                    "SECURITY_" + event.getOperation().replace(" ", "_")
            );
        } catch (Exception e) {
            log.error("发送安全告警失败: operation={}", event.getOperation(), e);
        }
    }
}
