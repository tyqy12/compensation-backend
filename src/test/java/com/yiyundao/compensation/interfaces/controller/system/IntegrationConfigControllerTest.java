package com.yiyundao.compensation.interfaces.controller.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.config.FileStorageProperties;
import com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto;
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
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

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
    @TempDir
    private Path tempDir;

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
    @DisplayName("POST 启用配置: 仅更新 enabled，不能要求前端重传敏感配置")
    void enableConfig_shouldOnlyEnableExistingConfigWithoutReEncrypt() {
        IntegrationConfig existing = new IntegrationConfig();
        existing.setPlatformType("yunzhanghu");
        existing.setEnabled(false);
        existing.setConfigJson("ENCRYPTED_CONFIG");
        when(integrationConfigService.getRawConfig("yunzhanghu")).thenReturn(existing);

        ApiResponse<String> response = controller.enableConfig("yunzhanghu", request);

        assertEquals(0, response.getCode());
        assertEquals("配置已启用", response.getData());
        verify(integrationConfigService).updateById(argThat(cfg ->
                "yunzhanghu".equals(cfg.getPlatformType())
                        && "ENCRYPTED_CONFIG".equals(cfg.getConfigJson())
                        && Boolean.TRUE.equals(cfg.getEnabled())
        ));
        verify(integrationConfigService, never()).saveOrUpdate(eq("yunzhanghu"), anyString(), eq(true));
    }

    @Test
    @DisplayName("POST 启用配置: 配置不存在时不能创建空配置")
    void enableConfig_shouldRejectMissingConfig() {
        when(integrationConfigService.getRawConfig("wechat")).thenReturn(null);

        ApiResponse<String> response = controller.enableConfig("wechat", request);

        assertEquals(ErrorCode.BUSINESS_ERROR.getCode(), response.getCode());
        verify(integrationConfigService, never()).updateById(argThat(cfg -> true));
        verify(integrationConfigService, never()).saveOrUpdate(eq("wechat"), anyString(), eq(true));
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
    @DisplayName("PUT 保存云账户: 前端回传脱敏值时应保留历史密钥，只更新明文变更字段")
    void saveConfig_shouldPreserveExistingSecretsWhenMaskedValuesSubmitted() throws Exception {
        String existingJson = "{\"dealerId\":\"dealer-1234\",\"brokerId\":\"broker-5678\",\"appKey\":\"appkey-9999\","
                + "\"des3Key\":\"DES3SECRET\",\"rsaPrivateKey\":\"PRIVATE\",\"rsaPublicKey\":\"PUBLIC\","
                + "\"signType\":\"rsa\",\"url\":\"https://old.example.com\",\"dealerPlatformName\":\"薪酬助手\"}";
        IntegrationConfig existing = new IntegrationConfig();
        existing.setPlatformType("yunzhanghu");
        existing.setEnabled(true);
        existing.setConfigJson("CIPHER_TEXT");
        when(integrationConfigService.getRawConfig("yunzhanghu")).thenReturn(existing);
        when(configDecryptionService.decrypt("CIPHER_TEXT")).thenReturn(existingJson);

        YunzhanghuConfigDto submitted = new YunzhanghuConfigDto();
        submitted.setDealerId("***1234");
        submitted.setBrokerId("***5678");
        submitted.setAppKey("***9999");
        submitted.setDes3Key("******");
        submitted.setRsaPrivateKey("******");
        submitted.setRsaPublicKey("******");
        submitted.setSignType("rsa2");
        submitted.setUrl("https://new.example.com");
        submitted.setDealerPlatformName("薪酬助手");

        IntegrationConfigController.SaveConfigRequest req = new IntegrationConfigController.SaveConfigRequest();
        req.setEnabled(true);
        req.setYunzhanghu(submitted);

        ApiResponse<String> response = controller.saveConfig("yunzhanghu", req, request);

        assertEquals(0, response.getCode());
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(integrationConfigService).saveOrUpdate(eq("yunzhanghu"), jsonCaptor.capture(), eq(true));
        YunzhanghuConfigDto saved = new ObjectMapper().readValue(jsonCaptor.getValue(), YunzhanghuConfigDto.class);
        assertEquals("dealer-1234", saved.getDealerId());
        assertEquals("broker-5678", saved.getBrokerId());
        assertEquals("appkey-9999", saved.getAppKey());
        assertEquals("DES3SECRET", saved.getDes3Key());
        assertEquals("PRIVATE", saved.getRsaPrivateKey());
        assertEquals("PUBLIC", saved.getRsaPublicKey());
        assertEquals("rsa2", saved.getSignType());
        assertEquals("https://new.example.com", saved.getUrl());
    }

    @Test
    @DisplayName("PUT 保存云账户: 没有历史配置时不能把脱敏占位符当作真实密钥")
    void saveConfig_shouldRejectMaskedValuesWithoutExistingSecrets() {
        when(integrationConfigService.getRawConfig("yunzhanghu")).thenReturn(null);

        YunzhanghuConfigDto submitted = new YunzhanghuConfigDto();
        submitted.setDealerId("***1234");
        submitted.setBrokerId("***5678");
        submitted.setAppKey("***9999");
        submitted.setDes3Key("******");
        submitted.setRsaPrivateKey("******");
        submitted.setRsaPublicKey("******");
        submitted.setSignType("rsa");
        submitted.setUrl("https://new.example.com");

        IntegrationConfigController.SaveConfigRequest req = new IntegrationConfigController.SaveConfigRequest();
        req.setEnabled(true);
        req.setYunzhanghu(submitted);

        ApiResponse<String> response = controller.saveConfig("yunzhanghu", req, request);

        assertEquals(ErrorCode.BUSINESS_ERROR.getCode(), response.getCode());
        verify(integrationConfigService, never()).saveOrUpdate(eq("yunzhanghu"), anyString(), eq(true));
    }

    @Test
    @DisplayName("PUT 保存支付宝: 占位私钥不能作为启用配置入库")
    void saveConfigShouldRejectPlaceholderAlipayPrivateKey() {
        AlipayConfigDto submitted = new AlipayConfigDto();
        submitted.setAppId("test-app-id");
        submitted.setPrivateKey("test-private-key");
        submitted.setPublicKey("alipay-public-key");

        IntegrationConfigController.SaveConfigRequest req = new IntegrationConfigController.SaveConfigRequest();
        req.setEnabled(true);
        req.setAlipay(submitted);

        ApiResponse<String> response = controller.saveConfig("alipay", req, request);

        assertEquals(ErrorCode.BUSINESS_ERROR.getCode(), response.getCode());
        assertTrue(response.getMessage().contains("PKCS8"));
        verify(integrationConfigService, never()).saveOrUpdate(eq("alipay"), anyString(), eq(true));
    }

    @Test
    @DisplayName("GET 列表: 单个平台探活抛 Error 时应降级 unknown，不能导致接口失败")
    void listConfigs_shouldGracefullyHandleHealthCheckError() {
        when(integrationConfigService.isPlatformEnabled("wechat")).thenReturn(false);
        when(integrationConfigService.isPlatformEnabled("dingtalk")).thenReturn(false);
        when(integrationConfigService.isPlatformEnabled("feishu")).thenReturn(false);
        when(integrationConfigService.isPlatformEnabled("alipay")).thenReturn(true);
        when(alipayService.checkAlipayConnection()).thenThrow(new RuntimeException("mock-health-check-failure"));

        ApiResponse<java.util.List<IntegrationConfigListDto>> response = controller.listConfigs(request);

        assertEquals(0, response.getCode());
        assertNotNull(response.getData());

        IntegrationConfigListDto alipayItem = response.getData().stream()
                .filter(item -> "alipay".equals(item.getPlatformType()))
                .findFirst()
                .orElseThrow();

        assertEquals("unknown", alipayItem.getConnectionStatus());
    }

    @Test
    @DisplayName("GET 列表: 未启用平台不应触发外部探活")
    void listConfigs_shouldNotProbeExternalConnectionsWhenPlatformDisabled() {
        ApiResponse<java.util.List<IntegrationConfigListDto>> response = controller.listConfigs(request);

        assertEquals(0, response.getCode());
        verify(organizationSyncService, never()).checkPlatformConnection(anyString());
        verify(alipayService, never()).checkAlipayConnection();
        verify(yunzhanghuClient, never()).healthCheck();
        verify(encryptionService, never()).checkEncryptionConfig();
    }

    @Test
    @DisplayName("支付宝证书上传: 空证书应拒绝")
    void uploadAlipayCert_shouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "app.crt",
                "application/x-x509-ca-cert",
                new byte[0]
        );

        ApiResponse<String> response = controller.uploadAlipayCert(file, "appCert", request);

        assertEquals(ErrorCode.BUSINESS_ERROR.getCode(), response.getCode());
        assertEquals("证书文件不能为空", response.getMessage());
    }

    @Test
    @DisplayName("支付宝证书上传: 合法证书应写入固定目录")
    void uploadAlipayCert_shouldWriteValidCertificate() throws Exception {
        FileStorageProperties.LocalStorage local = new FileStorageProperties.LocalStorage();
        local.setBasePath(tempDir.toString());
        when(fileStorageProperties.getLocal()).thenReturn(local);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "APP.CRT",
                "application/x-x509-ca-cert",
                VALID_X509_CERT.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        ApiResponse<String> response = controller.uploadAlipayCert(file, "appCert", request);

        Path expected = tempDir.resolve("certs/alipay/appCert.crt");
        assertEquals(0, response.getCode());
        assertEquals(expected.toAbsolutePath().normalize().toString(), response.getData());
        assertTrue(Files.exists(expected));
    }

    @Test
    @DisplayName("支付宝证书上传: .crt 后缀但内容非法应拒绝")
    void uploadAlipayCert_shouldRejectInvalidCertificateContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "app.crt",
                "application/x-x509-ca-cert",
                "not a certificate".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        ApiResponse<String> response = controller.uploadAlipayCert(file, "appCert", request);

        assertEquals(ErrorCode.BUSINESS_ERROR.getCode(), response.getCode());
        assertEquals("证书文件内容不是有效的 X.509 证书", response.getMessage());
    }

    private static final String VALID_X509_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIDGTCCAgGgAwIBAgIUPkYI/o60LtBeO8yIypkN5OjuANYwDQYJKoZIhvcNAQEL
            BQAwHDEaMBgGA1UEAwwRY29tcGVuc2F0aW9uLXRlc3QwHhcNMjYwNjA2MDQyMjEy
            WhcNMjYwNjA3MDQyMjEyWjAcMRowGAYDVQQDDBFjb21wZW5zYXRpb24tdGVzdDCC
            ASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALb3XzhtacySO7a8EHpraCN+
            zUS4GBXO30jfIgmfP/DWXcD8mnnXb1XQT8zq+zTbTAgyJOal8OUQcJ0b3rZD3MVJ
            u+k87HNymgTCkYwH1U/FCElbxsdxbf//BtFV69zXNSZXj0BWLt8P5N6sEXxPbfLZ
            GDTx4xsK8m19e49ddMHuCqheGJIZQBh2lxw/eJN+FmC6RywRxyHOmGFHlhq8qi9z
            RdoTTqDWbK4p7U0ybI2eh9p4yonKRTLSQKDsMbnjuYTaCQKXVKSVF8ksfkPDDoOm
            PWka97QMBeBZGpCYQr8f6eA6gI/HrYwYSwK8wSpXKOk+zccIaR4HsJD8EjqQsVsC
            AwEAAaNTMFEwHQYDVR0OBBYEFBobyZne/ez764nlP78hPqZEuARpMB8GA1UdIwQY
            MBaAFBobyZne/ez764nlP78hPqZEuARpMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZI
            hvcNAQELBQADggEBAEE0tqtr8nRhmSsaH8soM+dE5JcM52lsPBx80A+fbbw3wfPO
            eZFTpXnfczvQ2K1IcnpqFJwqiof7K9Rs4l+PQ3Dfkbse4ta3lA3BRokK+aHf4ULN
            hbLLPBjVfbpoWEQeWCwYKnccKDftAC8CVDGi/qJCgLQskETd2EYBmvEhBZOXg9T9
            kHeCRWUwjEl7LM5URuW0Nkd4xKwblM3BASCpwGvbC9dxV8TZ9snUuBVgGxoBy0un
            v3wtbITItgWSNJXa2AryR/WRPxKxq8OjVmgDHf3t5dki2QLFA+LpjQJyfhyV5T7c
            b1gH/ZPmhVS8NeamA4xE8tg971HVhRUnRgyXNL0=
            -----END CERTIFICATE-----
            """;
}
