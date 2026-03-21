package com.yiyundao.compensation.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.infrastructure.dao.IntegrationConfigMapper;
import com.yiyundao.compensation.interfaces.dto.config.WechatConfigDto;
import com.yiyundao.compensation.modules.system.entity.IntegrationConfig;
import com.yiyundao.compensation.service.ConfigDecryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IntegrationConfigServiceImpl 配置读取规则测试")
class IntegrationConfigServiceImplTest {

    @Mock
    private IntegrationConfigMapper integrationConfigMapper;

    @Mock
    private ConfigDecryptionService configDecryptionService;

    private IntegrationConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new IntegrationConfigServiceImpl(new ObjectMapper(), configDecryptionService);
        ReflectionTestUtils.setField(service, "baseMapper", integrationConfigMapper);
    }

    @Test
    @DisplayName("管理读取与运行时读取查询条件应区分：运行时更严格")
    void rawAndRuntimeRead_shouldUseDifferentQueryStrictness() {
        IntegrationConfig cfg = new IntegrationConfig();
        cfg.setEnabled(false);
        when(integrationConfigMapper.selectOne(any(), eq(true))).thenReturn(cfg, null);

        service.getRawConfig("yunzhanghu");
        service.getDecryptedConfig("yunzhanghu");

        ArgumentCaptor<LambdaQueryWrapper<IntegrationConfig>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(integrationConfigMapper, times(2)).selectOne(captor.capture(), eq(true));
        int rawQueryConditions = captor.getAllValues().get(0).getExpression().getNormal().size();
        int runtimeQueryConditions = captor.getAllValues().get(1).getExpression().getNormal().size();
        assertTrue(runtimeQueryConditions > rawQueryConditions, "运行时读取应比管理读取增加 enabled 条件过滤");
    }

    @Test
    @DisplayName("isPlatformEnabled: 由配置记录 enabled 字段决定")
    void isPlatformEnabled_shouldFollowEntityFlag() {
        IntegrationConfig disabled = new IntegrationConfig();
        disabled.setEnabled(false);
        IntegrationConfig enabled = new IntegrationConfig();
        enabled.setEnabled(true);
        when(integrationConfigMapper.selectOne(any(), eq(true))).thenReturn(disabled, enabled);

        assertFalse(service.isPlatformEnabled("wechat"));
        assertTrue(service.isPlatformEnabled("wechat"));
    }

    @Test
    @DisplayName("明文 JSON 配置应直接解析，不触发解密")
    void getWechatConfig_plainJsonShouldParseWithoutDecrypt() {
        IntegrationConfig cfg = new IntegrationConfig();
        cfg.setEnabled(true);
        cfg.setConfigJson("{\"corpId\":\"ww123\",\"corpSecret\":\"sec\",\"agentId\":\"1000003\"}");
        when(integrationConfigMapper.selectOne(any(), eq(true))).thenReturn(cfg);

        WechatConfigDto dto = service.getWechatConfig();

        assertNotNull(dto);
        assertEquals("ww123", dto.getCorpId());
        assertEquals("1000003", dto.getAgentId());
        verify(configDecryptionService, never()).decrypt(any());
    }
}
