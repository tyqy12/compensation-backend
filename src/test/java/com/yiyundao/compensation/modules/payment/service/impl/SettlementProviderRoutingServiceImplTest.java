package com.yiyundao.compensation.modules.payment.service.impl;

import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.infrastructure.dao.EmployeeTypeProviderMappingMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payment.entity.EmployeeTypeProviderMapping;
import com.yiyundao.compensation.modules.payment.entity.SettlementProviderConfig;
import com.yiyundao.compensation.modules.payment.service.SettlementProviderConfigService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementProviderRoutingServiceImpl 测试")
class SettlementProviderRoutingServiceImplTest {

    @Mock
    private EmployeeTypeProviderMappingMapper mappingMapper;

    @Mock
    private SettlementProviderConfigService configService;

    private SettlementProviderRoutingServiceImpl routingService;

    @BeforeEach
    void setUp() {
        routingService = new SettlementProviderRoutingServiceImpl(mappingMapper, configService);
    }

    @Test
    @DisplayName("员工级配置优先于批次与类型映射")
    void determineProvider_shouldUseEmployeeLevelProviderFirst() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setEmploymentType("part_time");
        employee.setSettlementProviderCode("yunzhanghu");

        when(configService.getConfigByCode("yunzhanghu")).thenReturn(enabledConfig("yunzhanghu"));

        String provider = routingService.determineProvider(employee, null);

        assertEquals("yunzhanghu", provider);
    }

    @Test
    @DisplayName("无员工级配置时使用批次配置")
    void determineProvider_shouldUseBatchProviderWhenEmployeeProviderMissing() {
        Employee employee = new Employee();
        employee.setId(2L);
        employee.setEmploymentType("part_time");

        PayrollBatch batch = new PayrollBatch();
        batch.setId(100L);
        batch.setSettlementProviderCode("alipay");

        when(configService.getConfigByCode("alipay")).thenReturn(enabledConfig("alipay"));

        String provider = routingService.determineProvider(employee, batch);

        assertEquals("alipay", provider);
    }

    @Test
    @DisplayName("无员工/批次配置时按员工类型映射路由")
    void determineProvider_shouldUseTypeMappingProvider() {
        Employee employee = new Employee();
        employee.setId(3L);
        employee.setEmploymentType("part_time");

        EmployeeTypeProviderMapping mapping = new EmployeeTypeProviderMapping();
        mapping.setEmploymentType(EmploymentType.PART_TIME);
        mapping.setProviderCode("yunzhanghu");
        mapping.setPriority(20);
        mapping.setEnabled(true);

        when(mappingMapper.selectList(any())).thenReturn(List.of(mapping));
        when(configService.getConfigByCode("yunzhanghu")).thenReturn(enabledConfig("yunzhanghu"));

        String provider = routingService.determineProvider(employee, null);

        assertEquals("yunzhanghu", provider);
    }

    @Test
    @DisplayName("非法用工类型抛出异常")
    void determineProvider_shouldThrowWhenEmploymentTypeInvalid() {
        Employee employee = new Employee();
        employee.setId(4L);
        employee.setEmploymentType("unknown_type");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> routingService.determineProvider(employee, null));
        assertTrue(ex.getMessage().contains("用工类型无效"));
    }

    @Test
    @DisplayName("映射列表中跳过禁用渠道并选择下一个可用渠道")
    void determineProvider_shouldSkipDisabledMapping() {
        Employee employee = new Employee();
        employee.setId(5L);
        employee.setEmploymentType("part_time");

        EmployeeTypeProviderMapping disabled = new EmployeeTypeProviderMapping();
        disabled.setEmploymentType(EmploymentType.PART_TIME);
        disabled.setProviderCode("yunzhanghu");
        disabled.setPriority(20);
        disabled.setEnabled(true);

        EmployeeTypeProviderMapping fallback = new EmployeeTypeProviderMapping();
        fallback.setEmploymentType(EmploymentType.PART_TIME);
        fallback.setProviderCode("alipay");
        fallback.setPriority(10);
        fallback.setEnabled(true);

        when(mappingMapper.selectList(any())).thenReturn(List.of(disabled, fallback));
        when(configService.getConfigByCode("yunzhanghu")).thenReturn(disabledConfig("yunzhanghu"));
        when(configService.getConfigByCode("alipay")).thenReturn(enabledConfig("alipay"));

        String provider = routingService.determineProvider(employee, null);

        assertEquals("alipay", provider);
    }

    private SettlementProviderConfig enabledConfig(String providerCode) {
        SettlementProviderConfig config = new SettlementProviderConfig();
        config.setProviderCode(providerCode);
        config.setEnabled(true);
        return config;
    }

    private SettlementProviderConfig disabledConfig(String providerCode) {
        SettlementProviderConfig config = new SettlementProviderConfig();
        config.setProviderCode(providerCode);
        config.setEnabled(false);
        return config;
    }
}
