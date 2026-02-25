package com.yiyundao.compensation.modules.app.service;

public interface AppRateLimitAlertNotifier {

    void notifyRateLimit(String clientId, String clientIp, long currentCount);
}

