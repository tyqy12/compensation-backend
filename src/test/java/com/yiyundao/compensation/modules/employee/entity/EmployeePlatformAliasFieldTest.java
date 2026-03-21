package com.yiyundao.compensation.modules.employee.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmployeePlatformAliasFieldTest {

    @Test
    void shouldSyncLegacyFieldsWhenSetNewFields() {
        Employee employee = new Employee();
        employee.setProvider("wechat");
        employee.setSubjectId("wx_user_1001");

        assertEquals("wechat", employee.getPlatformType());
        assertEquals("wx_user_1001", employee.getPlatformUserId());
    }

    @Test
    void shouldSyncNewFieldsWhenSetLegacyFields() {
        Employee employee = new Employee();
        employee.setPlatformType("dingtalk");
        employee.setPlatformUserId("ding_user_1002");

        assertEquals("dingtalk", employee.getProvider());
        assertEquals("ding_user_1002", employee.getSubjectId());
    }
}
