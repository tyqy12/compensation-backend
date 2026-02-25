package com.yiyundao.compensation.modules.app.service;

public interface AppRateLimitService {

    void checkRate(String clientId, String clientIp);
}

