package com.yiyundao.compensation.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformOAuthServicePlatformUserAliasTest {

    @Test
    void shouldKeepLegacyPlatformUserIdAccessorCompatible() {
        PlatformOAuthService.PlatformUser user = new PlatformOAuthService.PlatformUser();
        user.setSubjectId("wx_user_1001");
        assertEquals("wx_user_1001", user.getPlatformUserId());

        user.setPlatformUserId("wx_user_1002");
        assertEquals("wx_user_1002", user.getSubjectId());
    }

    @Test
    void shouldKeepLegacyPlatformAccessorCompatible() {
        PlatformOAuthService.PlatformUser user = new PlatformOAuthService.PlatformUser();
        user.setProvider("wechat");
        assertEquals("wechat", user.getPlatform());

        user.setPlatform("feishu");
        assertEquals("feishu", user.getProvider());
    }
}
