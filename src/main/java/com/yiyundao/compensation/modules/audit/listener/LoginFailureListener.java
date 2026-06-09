package com.yiyundao.compensation.modules.audit.listener;

import com.yiyundao.compensation.modules.audit.event.AuditLogSavedEvent;
import com.yiyundao.compensation.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败事件监听器
 * <p>
 * 监听登录相关操作，当检测到异常登录模式时触发告警。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginFailureListener {

    private final NotificationService notificationService;

    /**
     * 登录失败计数缓存 (username -> count)
     */
    private final Map<String, Integer> loginFailureCount = new ConcurrentHashMap<>();

    /**
     * 最后一次失败时间缓存
     */
    private final Map<String, LocalDateTime> lastFailureTime = new ConcurrentHashMap<>();

    /**
     * 登录失败阈值 (5次)
     */
    private static final int FAILURE_THRESHOLD = 5;

    /**
     * 时间窗口 (5分钟)
     */
    private static final int TIME_WINDOW_MINUTES = 5;
    private static final int MAX_TRACKED_USERS = 1000;

    /**
     * 处理审计日志保存事件
     */
    @Async
    @EventListener
    public void onAuditLogSaved(AuditLogSavedEvent event) {
        // 只处理登录相关操作
        if (!isLoginOperation(event)) {
            return;
        }

        // 只处理失败操作
        if (event.isSuccess()) {
            // 登录成功，清除失败计数
            loginFailureCount.remove(event.getUsername());
            lastFailureTime.remove(event.getUsername());
            return;
        }

        // 处理失败操作
        handleLoginFailure(event);
    }

    /**
     * 判断是否是登录操作
     */
    private boolean isLoginOperation(AuditLogSavedEvent event) {
        String operation = event.getOperation();
        return operation != null && (
                operation.contains("登录") ||
                operation.contains("LOGIN") ||
                operation.contains("login")
        );
    }

    /**
     * 处理登录失败
     */
    private void handleLoginFailure(AuditLogSavedEvent event) {
        String username = event.getUsername();
        if (username == null || username.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        pruneExpiredFailures(now);
        pruneOldestFailureKeysIfNeeded(username);
        LocalDateTime lastTime = lastFailureTime.get(username);

        // 检查是否在时间窗口内
        if (lastTime != null && lastTime.isAfter(now.minusMinutes(TIME_WINDOW_MINUTES))) {
            // 在时间窗口内，增加计数
            int count = loginFailureCount.getOrDefault(username, 0) + 1;
            loginFailureCount.put(username, count);
            lastFailureTime.put(username, now);

            log.warn("登录失败警告: username={}, count={}", username, count);

            // 达到阈值，触发告警
            if (count >= FAILURE_THRESHOLD) {
                triggerBruteForceAlert(username, count);
                // 告警后重置计数
                loginFailureCount.put(username, 0);
            }
        } else {
            // 不在时间窗口内，重置计数
            loginFailureCount.put(username, 1);
            lastFailureTime.put(username, now);
        }
    }

    /**
     * 触发暴力破解告警
     */
    private void triggerBruteForceAlert(String username, int failureCount) {
        log.warn("🚨 检测到可能的暴力破解攻击: username={}, failureCount={}", username, failureCount);

        try {
            String content = String.format(
                    "检测到异常登录行为\n\n" +
                            "用户名：%s\n" +
                            "失败次数：%d 次\n" +
                            "时间窗口：%d 分钟\n\n" +
                            "建议：检查是否存在暴力破解攻击，或用户密码是否泄露。",
                    username,
                    failureCount,
                    TIME_WINDOW_MINUTES
            );

            notificationService.sendSystemAlert(
                    "登录安全告警",
                    content,
                    "LOGIN_SECURITY_" + username
            );
        } catch (Exception e) {
            log.error("发送登录安全告警失败", e);
        }
    }

    /**
     * 获取当前登录失败计数（用于监控）
     */
    public Map<String, Integer> getLoginFailureCount() {
        return Map.copyOf(loginFailureCount);
    }

    /**
     * 清除指定用户的失败计数（管理员手动处理后调用）
     */
    public void clearFailureCount(String username) {
        loginFailureCount.remove(username);
        lastFailureTime.remove(username);
    }

    private void pruneExpiredFailures(LocalDateTime now) {
        LocalDateTime threshold = now.minusMinutes(TIME_WINDOW_MINUTES);
        lastFailureTime.entrySet().removeIf(entry -> {
            LocalDateTime failureTime = entry.getValue();
            boolean expired = failureTime == null || failureTime.isBefore(threshold);
            if (expired) {
                loginFailureCount.remove(entry.getKey());
            }
            return expired;
        });
    }

    private void pruneOldestFailureKeysIfNeeded(String username) {
        if (loginFailureCount.containsKey(username)) {
            return;
        }
        int overflow = loginFailureCount.size() - MAX_TRACKED_USERS + 1;
        if (overflow <= 0) {
            return;
        }
        lastFailureTime.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(overflow)
                .map(Map.Entry::getKey)
                .forEach(key -> {
                    loginFailureCount.remove(key);
                    lastFailureTime.remove(key);
                });
    }
}
