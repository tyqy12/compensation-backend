package com.yiyundao.compensation.modules.app.service.impl;

import com.yiyundao.compensation.modules.app.service.AppRateLimitAlertNotifier;
import com.yiyundao.compensation.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class NotificationRateLimitAlertNotifier implements AppRateLimitAlertNotifier {

    private final NotificationService notificationService;

    @Override
    public void notifyRateLimit(String clientId, String clientIp, long currentCount) {
        String normalizedIp = StringUtils.hasText(clientIp) ? clientIp : "unknown";
        String alertMessage = String.format("外部应用(clientId=%s, ip=%s) 在1分钟内触发限流，当前计数=%d", clientId, normalizedIp, currentCount);
        notificationService.sendSystemAlert("外部API限流触发", alertMessage, "APP_RATE_LIMIT_" + clientId + "|" + normalizedIp);
    }
}

