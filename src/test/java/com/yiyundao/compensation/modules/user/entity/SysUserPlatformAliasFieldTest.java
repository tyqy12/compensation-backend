package com.yiyundao.compensation.modules.user.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SysUserPlatformAliasFieldTest {

    @Test
    void shouldSyncLegacyFieldsWhenSetNewFields() {
        SysUser user = new SysUser();
        user.setProvider("wechat");
        user.setSubjectId("wx_user_1001");

        assertEquals("wechat", user.getPlatformType());
        assertEquals("wx_user_1001", user.getPlatformUserId());
    }

    @Test
    void shouldSyncNewFieldsWhenSetLegacyFields() {
        SysUser user = new SysUser();
        user.setPlatformType("dingtalk");
        user.setPlatformUserId("ding_user_1002");

        assertEquals("dingtalk", user.getProvider());
        assertEquals("ding_user_1002", user.getSubjectId());
    }
}
