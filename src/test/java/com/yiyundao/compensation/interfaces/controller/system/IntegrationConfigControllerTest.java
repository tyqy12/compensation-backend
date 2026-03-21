package com.yiyundao.compensation.interfaces.controller.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.config.FileStorageProperties;
import com.yiyundao.compensation.interfaces.dto.config.IntegrationConfigDetailDto;
import com.yiyundao.compensation.interfaces.dto.config.IntegrationConfigListDto;
import com.yiyundao.compensation.interfaces.dto.config.YunzhanghuConfigDto;
import com.yiyundao.compensation.modules.system.entity.IntegrationConfig;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yiyundao.compensation.service.AlipayService;
import com.yiyundao.compensation.service.ConfigDecryptionService;
import com.yiyundao.compensation.service.EncryptionService;
import com.yiyundao.compensation.service.FileService;
import com.yiyundao.compensation.service.OrganizationSyncService;
import com.yiyundao.compensation.service.YunzhanghuClient;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IntegrationConfigController CRUD 行为测试")
class IntegrationConfigControllerTest {

    @Mock
    private IntegrationConfigService integrationConfigService;
    @Mock
    private com.yiyundao.compensation.modules.audit.service.AuditLogService auditLogService;
    @Mock
    private OrganizationSyncService organizationSyncService;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private AlipayService alipayService;
    @Mock
    private YunzhanghuClient yunzhanghuClient;
    @Mock
    private ConfigDecryptionService configDecryptionService;
    @Mock
    private FileService fileService;
    @Mock
    private FileStorageProperties fileStorageProperties;
    @Mock
    private HttpServletRequest request;

    private IntegrationConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new IntegrationConfigController(
                integrationConfigService,
                new ObjectMapper(),
                auditLogService,
                organizationSyncService,
                encryptionService,
                alipayService,
                yunzhanghuClient,
                configDecryptionService,
                fileService,
                fileStorageProperties
        );
    }

    @Test
    @DisplayName("DELETE 禁用配置: 仅更新 enabled，不能二次加密覆盖 configJson")
    void deleteConfig_shouldOnlyDisableWithoutReEncrypt() {
        IntegrationConfig existing = new IntegrationConfig();
        existing.setPlatformType("yunzhanghu");
        existing.setEnabled(true);
        existing.setConfigJson("ENCRYPTED_CONFIG");
        when(integrationConfigService.getRawConfig("yunzhanghu")).thenReturn(existing);

        ApiResponse<String> response = controller.deleteConfig("yunzhanghu", request);

        assertEquals(0, response.getCode());
        verify(integrationConfigService).updateById(argThat(cfg ->
                "yunzhanghu".equals(cfg.getPlatformType())
                        && "ENCRYPTED_CONFIG".equals(cfg.getConfigJson())
                        && Boolean.FALSE.equals(cfg.getEnabled())
        ));
        verify(integrationConfigService, never()).saveOrUpdate(eq("yunzhanghu"), anyString(), eq(false));
    }

    @Test
    @DisplayName("PUT enabled=false: 已存在配置时应保留配置内容，仅切换禁用")
    void saveConfigDisable_shouldKeepExistingCiphertext() {
        IntegrationConfig existing = new IntegrationConfig();
        existing.setPlatformType("yunzhanghu");
        existing.setEnabled(true);
        existing.setConfigJson("ENCRYPTED_CONFIG");
        when(integrationConfigService.getRawConfig("yunzhanghu")).thenReturn(existing);

        IntegrationConfigController.SaveConfigRequest req = new IntegrationConfigController.SaveConfigRequest();
        req.setEnabled(false);
        ApiResponse<String> response = controller.saveConfig("yunzhanghu", req, request);

        assertEquals(0, response.getCode());
        verify(integrationConfigService).updateById(argThat(cfg ->
                "ENCRYPTED_CONFIG".equals(cfg.getConfigJson())
                        && Boolean.FALSE.equals(cfg.getEnabled())
        ));
        verify(integrationConfigService, never()).saveOrUpdate(eq("yunzhanghu"), eq("{}"), eq(false));
    }

    @Test
    @DisplayName("GET 详情: 禁用配置仍可读取并脱敏回显，支持前端继续编辑")
    void getConfig_shouldReturnMaskedConfigWhenDisabled() {
        IntegrationConfig existing = new IntegrationConfig();
        existing.setPlatformType("yunzhanghu");
        existing.setEnabled(false);
        existing.setConfigJson("CIPHER_TEXT");
        when(integrationConfigService.getRawConfig("yunzhanghu")).thenReturn(existing);
        when(configDecryptionService.decrypt("CIPHER_TEXT")).thenReturn(
                "{\"dealerId\":\"dealer-1234\",\"brokerId\":\"broker-5678\",\"appKey\":\"appkey-9999\"," +
                        "\"des3Key\":\"DES3SECRET\",\"rsaPrivateKey\":\"PRIVATE\",\"rsaPublicKey\":\"PUBLIC\"," +
                        "\"signType\":\"rsa\",\"url\":\"https://sandbox.example.com\"}"
        );

        ApiResponse<?> response = controller.getConfig("yunzhanghu", request);

        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
        IntegrationConfigDetailDto detail = (IntegrationConfigDetailDto) response.getData();
        assertFalse(detail.getEnabled());
        assertTrue(detail.getConfig() instanceof YunzhanghuConfigDto);

        YunzhanghuConfigDto masked = (YunzhanghuConfigDto) detail.getConfig();
        assertEquals("***1234", masked.getDealerId());
        assertEquals("***5678", masked.getBrokerId());
        assertEquals("***9999", masked.getAppKey());
        assertEquals("******", masked.getDes3Key());
        assertEquals("******", masked.getRsaPrivateKey());
        assertEquals("******", masked.getRsaPublicKey());
    }

    @Test
    @DisplayName("GET 列表: 单个平台探活抛 Error 时应降级 unknown，不能导致接口失败")
    void listConfigs_shouldGracefullyHandleHealthCheckError() {
        when(alipayService.checkAlipayConnection()).thenThrow(new NoClassDefFoundError("mock-missing-sdk"));

        ApiResponse<java.util.List<IntegrationConfigListDto>> response = controller.listConfigs(request);

        assertEquals(0, response.getCode());
        assertNotNull(response.getData());

        IntegrationConfigListDto alipayItem = response.getData().stream()
                .filter(item -> "alipay".equals(item.getPlatformType()))
                .findFirst()
                .orElseThrow();

        assertEquals("unknown", alipayItem.getConnectionStatus());
    }
}
