package com.yiyundao.compensation.interfaces.vo.employee;

import com.yiyundao.compensation.modules.employee.entity.Employee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmployeeListItemVOTest {

    @Test
    void shouldTranslateKnownPlatformCode() {
        Employee employee = new Employee();
        employee.setProvider("wechat");

        EmployeeListItemVO vo = EmployeeListItemVO.from(employee);

        assertEquals("企业微信", vo.getProviderName());
    }

    @Test
    void shouldFallbackToOriginalPlatformCodeWhenUnknown() {
        Employee employee = new Employee();
        employee.setProvider("custom_platform");

        EmployeeListItemVO vo = EmployeeListItemVO.from(employee);

        assertEquals("custom_platform", vo.getProviderName());
    }

    @Test
    void shouldKeepNullWhenPlatformCodeIsNull() {
        Employee employee = new Employee();
        employee.setProvider(null);

        EmployeeListItemVO vo = EmployeeListItemVO.from(employee);

        assertNull(vo.getProviderName());
    }
}
