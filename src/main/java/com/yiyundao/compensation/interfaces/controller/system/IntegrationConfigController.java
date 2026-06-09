package com.yiyundao.compensation.interfaces.controller.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.utils.AlipayKeyFormatValidator;
import com.yiyundao.compensation.interfaces.dto.config.*;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.modules.system.entity.IntegrationConfig;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/integration-configs")
@PreAuthorize("hasRole('ADMIN')")
public class IntegrationConfigController {

    private final IntegrationConfigService integrationConfigService;
    private final ObjectMapper objectMapper;
    private final com.yiyundao.compensation.modules.audit.service.AuditLogService auditLogService;
    private final com.yiyundao.compensation.service.OrganizationSyncService organizationSyncService;
    private final com.yiyundao.compensation.service.EncryptionService encryptionService;
    private final com.yiyundao.compensation.service.AlipayService alipayService;
    private final com.yiyundao.compensation.service.YunzhanghuClient yunzhanghuClient;
    private final com.yiyundao.compensation.service.ConfigDecryptionService configDecryptionService;
    private final com.yiyundao.compensation.service.FileService fileService;
    private final com.yiyundao.compensation.config.FileStorageProperties fileStorageProperties;

    /**
     * 获取所有集成配置列表
     */
    @GetMapping
    public ApiResponse<java.util.List<IntegrationConfigListDto>> listConfigs(jakarta.servlet.http.HttpServletRequest request) {
        try {
            java.util.List<IntegrationConfigListDto> configs = new java.util.ArrayList<>();

            // 支持的平台类型
            String[] platforms = {"wechat", "dingtalk", "feishu", "alipay", "yunzhanghu", "sms", "email", "encryption"};

            for (String platform : platforms) {
                IntegrationConfig rawConfig = integrationConfigService.getRawConfig(platform);

                IntegrationConfigListDto dto = new IntegrationConfigListDto();
                dto.setPlatformType(platform);
                dto.setPlatformName(getPlatformDisplayName(platform));
                dto.setEnabled(rawConfig != null && Boolean.TRUE.equals(rawConfig.getEnabled()));
                dto.setConfigured(rawConfig != null && rawConfig.getConfigJson() != null);
                dto.setLastModified(rawConfig != null ? rawConfig.getUpdateTime() : null);

                // 测试连接状态
                dto.setConnectionStatus(testPlatformConnection(platform));

                configs.add(dto);
            }

            auditSafe("INTEGRATION_CONFIG_LIST", "ALL", request, true, null);
            return ApiResponse.success(configs);
        } catch (Exception e) {
            log.error("获取配置列表失败", e);
            auditSafe("INTEGRATION_CONFIG_LIST", "ALL", request, false, e.getMessage());
            return ApiResponse.error("获取配置列表失败");
        }
    }

    @GetMapping("/{platformType}")
    public ApiResponse<?> getConfig(@PathVariable String platformType, jakarta.servlet.http.HttpServletRequest request) {
        try {
            IntegrationConfig cfg = integrationConfigService.getRawConfig(platformType);
            if (cfg == null) {
                // 配置不存在时返回默认空对象，避免前端因 data 为 null 而报错
                auditSafe("INTEGRATION_CONFIG_READ", platformType, request, true, "配置不存在，返回默认对象");
                IntegrationConfigDetailDto emptyResult = new IntegrationConfigDetailDto();
                emptyResult.setPlatformType(platformType);
                emptyResult.setPlatformName(getPlatformDisplayName(platformType));
                emptyResult.setEnabled(false);
                emptyResult.setConfig(null);
                emptyResult.setConnectionStatus("disconnected");
                emptyResult.setLastModified(null);
                return ApiResponse.success(emptyResult);
            }

            Object configDto = buildMaskedConfig(platformType, cfg);

            // 包装返回数据，包含平台状态信息
            IntegrationConfigDetailDto result = new IntegrationConfigDetailDto();
            result.setPlatformType(platformType);
            result.setPlatformName(getPlatformDisplayName(platformType));
            result.setEnabled(Boolean.TRUE.equals(cfg.getEnabled()));
            result.setConfig(configDto);
            result.setLastModified(cfg.getUpdateTime());
            result.setConnectionStatus(testPlatformConnection(platformType));

            auditSafe("INTEGRATION_CONFIG_READ", platformType, request, true, null);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("读取配置失败", e);
            auditSafe("INTEGRATION_CONFIG_READ", platformType, request, false, e.getMessage());
            return ApiResponse.error("读取配置失败: " + e.getMessage());
        }
    }

    @PutMapping("/{platformType}")
    public ApiResponse<String> saveConfig(@PathVariable String platformType,
                                          @RequestBody SaveConfigRequest req,
                                          jakarta.servlet.http.HttpServletRequest request) {
        try {
            if (req == null) {
                return ApiResponse.error("配置请求不能为空");
            }
            // 如果禁用配置，可以不传具体配置内容
            if (Boolean.FALSE.equals(req.getEnabled())) {
                disablePlatform(platformType, true);
                if ("encryption".equals(platformType)) {
                    encryptionService.forceRefreshKeys();
                }
                auditSafe("INTEGRATION_CONFIG_DISABLE", platformType, request, true, null);
                return ApiResponse.success("配置已禁用");
            }

            String json;
            switch (platformType) {
                case "wechat":
                    if (req.getWechat() == null) {
                        return ApiResponse.error("启用微信时配置不能为空");
                    }
                    WechatConfigDto wechat = mergeWechatConfig(platformType, req.getWechat());
                    // 验证必填字段
                    if (!StringUtils.hasText(wechat.getCorpId()) || !StringUtils.hasText(wechat.getCorpSecret())) {
                        return ApiResponse.error("微信配置缺少必填字段：corpId, corpSecret");
                    }
                    json = objectMapper.writeValueAsString(wechat);
                    break;
                case "dingtalk":
                    if (req.getDingtalk() == null) {
                        return ApiResponse.error("启用钉钉时配置不能为空");
                    }
                    DingTalkConfigDto dingtalk = mergeDingTalkConfig(platformType, req.getDingtalk());
                    if (!StringUtils.hasText(dingtalk.getAppKey()) || !StringUtils.hasText(dingtalk.getAppSecret())) {
                        return ApiResponse.error("钉钉配置缺少必填字段：appKey, appSecret");
                    }
                    json = objectMapper.writeValueAsString(dingtalk);
                    break;
                case "feishu":
                    if (req.getFeishu() == null) {
                        return ApiResponse.error("启用飞书时配置不能为空");
                    }
                    FeishuConfigDto feishu = mergeFeishuConfig(platformType, req.getFeishu());
                    if (!StringUtils.hasText(feishu.getAppId()) || !StringUtils.hasText(feishu.getAppSecret())) {
                        return ApiResponse.error("飞书配置缺少必填字段：appId, appSecret");
                    }
                    json = objectMapper.writeValueAsString(feishu);
                    break;
                case "alipay":
                    if (req.getAlipay() == null) {
                        return ApiResponse.error("启用支付宝时配置不能为空");
                    }
                    AlipayConfigDto alipay = mergeAlipayConfig(platformType, req.getAlipay());
                    if (!StringUtils.hasText(alipay.getAppId()) || !StringUtils.hasText(alipay.getPrivateKey())) {
                        return ApiResponse.error("支付宝配置缺少必填字段：appId, privateKey");
                    }
                    try {
                        alipay.setPrivateKey(AlipayKeyFormatValidator.normalizePkcs8PrivateKey(alipay.getPrivateKey()));
                    } catch (IllegalStateException ex) {
                        return ApiResponse.error(ex.getMessage());
                    }

                    // 根据加签模式验证必填字段
                    boolean isCertMode = "cert".equalsIgnoreCase(alipay.getCertMode());
                    if (isCertMode) {
                        // 证书模式：验证三个证书路径
                        if (!StringUtils.hasText(alipay.getAppCertPath()) ||
                            !StringUtils.hasText(alipay.getAlipayCertPath()) ||
                            !StringUtils.hasText(alipay.getAlipayRootCertPath())) {
                            return ApiResponse.error("证书模式需要配置应用公钥证书、支付宝公钥证书和支付宝根证书路径");
                        }
                    } else {
                        // 公钥模式：验证支付宝公钥
                        if (!StringUtils.hasText(alipay.getPublicKey())) {
                            return ApiResponse.error("公钥模式需要配置支付宝公钥");
                        }
                    }

                    json = objectMapper.writeValueAsString(alipay);
                    break;
                case "yunzhanghu":
                    if (req.getYunzhanghu() == null) {
                        return ApiResponse.error("启用云账户时配置不能为空");
                    }
                    YunzhanghuConfigDto yunzhanghu = mergeYunzhanghuConfig(platformType, req.getYunzhanghu());
                    if (!StringUtils.hasText(yunzhanghu.getDealerId()) ||
                            !StringUtils.hasText(yunzhanghu.getBrokerId()) ||
                            !StringUtils.hasText(yunzhanghu.getAppKey()) ||
                            !StringUtils.hasText(yunzhanghu.getDes3Key()) ||
                            !StringUtils.hasText(yunzhanghu.getRsaPrivateKey()) ||
                            !StringUtils.hasText(yunzhanghu.getRsaPublicKey()) ||
                            !StringUtils.hasText(yunzhanghu.getUrl()) ||
                            !StringUtils.hasText(yunzhanghu.getSignType())) {
                        return ApiResponse.error("云账户配置缺少必填字段：dealerId, brokerId, appKey, 3desKey, rsaPrivateKey, rsaPublicKey, url, signType");
                    }
                    json = objectMapper.writeValueAsString(yunzhanghu);
                    break;
                case "sms":
                    if (req.getSms() == null) {
                        return ApiResponse.error("启用短信时配置不能为空");
                    }
                    SmsConfigDto sms = mergeSmsConfig(platformType, req.getSms());
                    if (!StringUtils.hasText(sms.getProvider())) {
                        return ApiResponse.error("短信配置缺少必填字段：provider");
                    }
                    json = objectMapper.writeValueAsString(sms);
                    break;
                case "email":
                    if (req.getEmail() == null) {
                        return ApiResponse.error("启用邮件时配置不能为空");
                    }
                    EmailConfigDto email = mergeEmailConfig(platformType, req.getEmail());
                    if (!StringUtils.hasText(email.getHost()) || !StringUtils.hasText(email.getUsername())) {
                        return ApiResponse.error("邮件配置缺少必填字段：host, username");
                    }
                    json = objectMapper.writeValueAsString(email);
                    break;
                case "encryption":
                    if (req.getEncryption() == null) {
                        return ApiResponse.error("启用加密时配置不能为空");
                    }
                    EncryptionConfigDto encryption = mergeEncryptionConfig(platformType, req.getEncryption());
                    // 加密配置比较特殊，至少需要一个密钥
                    if (!StringUtils.hasText(encryption.getAesKey()) && !StringUtils.hasText(encryption.getSm4Key())) {
                        return ApiResponse.error("加密配置至少需要设置 aesKey 或 sm4Key");
                    }
                    json = objectMapper.writeValueAsString(encryption);
                    break;
                default:
                    return ApiResponse.error("不支持的平台类型: " + platformType);
            }

            integrationConfigService.saveOrUpdate(platformType, json, Boolean.TRUE.equals(req.getEnabled()));

            // 对于加密服务，强制刷新密钥缓存
            if ("encryption".equals(platformType)) {
                encryptionService.forceRefreshKeys();
            }

            auditSafe("INTEGRATION_CONFIG_SAVE", platformType, request, true, null);
            return ApiResponse.success("配置保存成功");
        } catch (Exception e) {
            log.error("保存配置失败", e);
            auditSafe("INTEGRATION_CONFIG_SAVE", platformType, request, false, e.getMessage());
            return ApiResponse.error("保存配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除配置（实际上是禁用配置）
     */
    @DeleteMapping("/{platformType}")
    public ApiResponse<String> deleteConfig(@PathVariable String platformType, jakarta.servlet.http.HttpServletRequest request) {
        try {
            if (!disablePlatform(platformType, false)) {
                return ApiResponse.error("配置不存在");
            }

            auditSafe("INTEGRATION_CONFIG_DELETE", platformType, request, true, null);
            return ApiResponse.success("配置已禁用");
        } catch (Exception e) {
            log.error("删除配置失败", e);
            auditSafe("INTEGRATION_CONFIG_DELETE", platformType, request, false, e.getMessage());
            return ApiResponse.error("删除配置失败: " + e.getMessage());
        }
    }

    /**
     * 启用已有配置。
     * <p>仅切换 enabled 状态，保留原密文配置，避免前端为启用操作重新提交敏感配置。</p>
     */
    @PostMapping("/{platformType}/enable")
    public ApiResponse<String> enableConfig(@PathVariable String platformType, jakarta.servlet.http.HttpServletRequest request) {
        try {
            IntegrationConfig cfg = integrationConfigService.getRawConfig(platformType);
            if (cfg == null || !StringUtils.hasText(cfg.getConfigJson())) {
                auditSafe("INTEGRATION_CONFIG_ENABLE", platformType, request, false, "配置不存在");
                return ApiResponse.error("配置不存在，请先完成配置");
            }

            cfg.setEnabled(true);
            integrationConfigService.updateById(cfg);
            if ("encryption".equals(platformType)) {
                encryptionService.forceRefreshKeys();
            }

            auditSafe("INTEGRATION_CONFIG_ENABLE", platformType, request, true, null);
            return ApiResponse.success("配置已启用");
        } catch (Exception e) {
            log.error("启用配置失败", e);
            auditSafe("INTEGRATION_CONFIG_ENABLE", platformType, request, false, e.getMessage());
            return ApiResponse.error("启用配置失败: " + e.getMessage());
        }
    }

    // 连接性测试（使用当前数据库中的配置）
    @PostMapping("/{platformType}/test-connection")
    public ApiResponse<Boolean> testConnection(@PathVariable String platformType,
                                              jakarta.servlet.http.HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        try {
            boolean ok = testPlatformConnection(platformType).equals("connected");
            auditLogService.record("检查平台连接", request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), request.getHeader("User-Agent"),
                    "INTEGRATION", platformType, request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                    "platform=" + platformType, ok ? "OK" : "FAILED", null, System.currentTimeMillis() - begin);
            return ApiResponse.success(ok);
        } catch (Exception e) {
            auditLogService.record("检查平台连接", request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), request.getHeader("User-Agent"),
                    "INTEGRATION", platformType, request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                    "platform=" + platformType, "FAILED", e.getMessage(), System.currentTimeMillis() - begin);
            return ApiResponse.error("连接测试失败: " + e.getMessage());
        }
    }

    @Data
    public static class SaveConfigRequest {
        private Boolean enabled;
        private WechatConfigDto wechat;
        private DingTalkConfigDto dingtalk;
        private FeishuConfigDto feishu;
        private AlipayConfigDto alipay;
        private YunzhanghuConfigDto yunzhanghu;
        private SmsConfigDto sms;
        private EmailConfigDto email;
        private EncryptionConfigDto encryption;
    }

    private void auditSafe(String op, String platform, jakarta.servlet.http.HttpServletRequest req, boolean success, String err) {
        try {
            auditLogService.record(op, req.getMethod(), req.getRequestURI(), req.getRemoteAddr(), req.getHeader("User-Agent"),
                    "INTEGRATION", platform, req.getUserPrincipal() != null ? req.getUserPrincipal().getName() : null,
                    "platform=" + platform, success ? "OK" : "FAILED", err, 0L);
        } catch (Exception ignore) {}
    }

    private boolean disablePlatform(String platformType, boolean createIfMissing) {
        IntegrationConfig cfg = integrationConfigService.getRawConfig(platformType);
        if (cfg == null) {
            if (!createIfMissing) {
                return false;
            }
            integrationConfigService.saveOrUpdate(platformType, "{}", false);
            return true;
        }
        cfg.setEnabled(false);
        integrationConfigService.updateById(cfg);
        return true;
    }

    private WechatConfigDto mergeWechatConfig(String platformType, WechatConfigDto incoming) {
        WechatConfigDto existing = parseExistingConfig(platformType, WechatConfigDto.class);
        WechatConfigDto c = new WechatConfigDto();
        c.setCorpId(keepExistingWhenMaskedOrBlank(incoming.getCorpId(), existing != null ? existing.getCorpId() : null));
        c.setCorpSecret(keepExistingWhenMaskedOrBlank(incoming.getCorpSecret(), existing != null ? existing.getCorpSecret() : null));
        c.setAgentId(incoming.getAgentId());
        return c;
    }

    private DingTalkConfigDto mergeDingTalkConfig(String platformType, DingTalkConfigDto incoming) {
        DingTalkConfigDto existing = parseExistingConfig(platformType, DingTalkConfigDto.class);
        DingTalkConfigDto c = new DingTalkConfigDto();
        c.setAppKey(keepExistingWhenMaskedOrBlank(incoming.getAppKey(), existing != null ? existing.getAppKey() : null));
        c.setAppSecret(keepExistingWhenMaskedOrBlank(incoming.getAppSecret(), existing != null ? existing.getAppSecret() : null));
        c.setWebhookUrl(keepExistingWhenMaskedOrBlank(incoming.getWebhookUrl(), existing != null ? existing.getWebhookUrl() : null));
        c.setEnabled(incoming.getEnabled());
        return c;
    }

    private FeishuConfigDto mergeFeishuConfig(String platformType, FeishuConfigDto incoming) {
        FeishuConfigDto existing = parseExistingConfig(platformType, FeishuConfigDto.class);
        FeishuConfigDto c = new FeishuConfigDto();
        c.setAppId(keepExistingWhenMaskedOrBlank(incoming.getAppId(), existing != null ? existing.getAppId() : null));
        c.setAppSecret(keepExistingWhenMaskedOrBlank(incoming.getAppSecret(), existing != null ? existing.getAppSecret() : null));
        c.setAppToken(keepExistingWhenMaskedOrBlank(incoming.getAppToken(), existing != null ? existing.getAppToken() : null));
        c.setEnabled(incoming.getEnabled());
        return c;
    }

    private AlipayConfigDto mergeAlipayConfig(String platformType, AlipayConfigDto incoming) {
        AlipayConfigDto existing = parseExistingConfig(platformType, AlipayConfigDto.class);
        AlipayConfigDto c = new AlipayConfigDto();
        c.setAppId(keepExistingWhenMaskedOrBlank(incoming.getAppId(), existing != null ? existing.getAppId() : null));
        c.setServerUrl(incoming.getServerUrl());
        c.setPrivateKey(keepExistingWhenMaskedOrBlank(incoming.getPrivateKey(), existing != null ? existing.getPrivateKey() : null));
        c.setPublicKey(keepExistingWhenMaskedOrBlank(incoming.getPublicKey(), existing != null ? existing.getPublicKey() : null));
        c.setCharset(incoming.getCharset());
        c.setSignType(incoming.getSignType());
        c.setFormat(incoming.getFormat());
        c.setNotifyUrl(incoming.getNotifyUrl());
        c.setReturnUrl(incoming.getReturnUrl());
        c.setEncryptKey(keepExistingWhenMaskedOrBlank(incoming.getEncryptKey(), existing != null ? existing.getEncryptKey() : null));
        c.setEncryptType(incoming.getEncryptType());
        c.setCertMode(incoming.getCertMode());
        c.setAppCertPath(incoming.getAppCertPath());
        c.setAlipayCertPath(incoming.getAlipayCertPath());
        c.setAlipayRootCertPath(incoming.getAlipayRootCertPath());
        c.setConnectTimeout(incoming.getConnectTimeout());
        c.setReadTimeout(incoming.getReadTimeout());
        c.setSingleLimit(incoming.getSingleLimit());
        c.setDailyLimit(incoming.getDailyLimit());
        c.setRealNameVerify(incoming.getRealNameVerify());
        return c;
    }

    private YunzhanghuConfigDto mergeYunzhanghuConfig(String platformType, YunzhanghuConfigDto incoming) {
        YunzhanghuConfigDto existing = parseExistingConfig(platformType, YunzhanghuConfigDto.class);
        YunzhanghuConfigDto c = new YunzhanghuConfigDto();
        c.setDealerId(keepExistingWhenMaskedOrBlank(incoming.getDealerId(), existing != null ? existing.getDealerId() : null));
        c.setBrokerId(keepExistingWhenMaskedOrBlank(incoming.getBrokerId(), existing != null ? existing.getBrokerId() : null));
        c.setAppKey(keepExistingWhenMaskedOrBlank(incoming.getAppKey(), existing != null ? existing.getAppKey() : null));
        c.setDes3Key(keepExistingWhenMaskedOrBlank(incoming.getDes3Key(), existing != null ? existing.getDes3Key() : null));
        c.setRsaPrivateKey(keepExistingWhenMaskedOrBlank(incoming.getRsaPrivateKey(), existing != null ? existing.getRsaPrivateKey() : null));
        c.setRsaPublicKey(keepExistingWhenMaskedOrBlank(incoming.getRsaPublicKey(), existing != null ? existing.getRsaPublicKey() : null));
        c.setSignType(incoming.getSignType());
        c.setUrl(incoming.getUrl());
        c.setNotifyUrl(incoming.getNotifyUrl());
        c.setProjectId(incoming.getProjectId());
        c.setCheckName(incoming.getCheckName());
        c.setDealerPlatformName(incoming.getDealerPlatformName());
        c.setIsDebug(incoming.getIsDebug());
        return c;
    }

    private SmsConfigDto mergeSmsConfig(String platformType, SmsConfigDto incoming) {
        SmsConfigDto existing = parseExistingConfig(platformType, SmsConfigDto.class);
        SmsConfigDto c = new SmsConfigDto();
        c.setProvider(incoming.getProvider());
        c.setAccessKeyId(keepExistingWhenMaskedOrBlank(incoming.getAccessKeyId(), existing != null ? existing.getAccessKeyId() : null));
        c.setAccessKeySecret(keepExistingWhenMaskedOrBlank(incoming.getAccessKeySecret(), existing != null ? existing.getAccessKeySecret() : null));
        c.setSignName(incoming.getSignName());
        c.setTemplateCode(incoming.getTemplateCode());
        c.setSecretId(keepExistingWhenMaskedOrBlank(incoming.getSecretId(), existing != null ? existing.getSecretId() : null));
        c.setSecretKey(keepExistingWhenMaskedOrBlank(incoming.getSecretKey(), existing != null ? existing.getSecretKey() : null));
        c.setAppId(incoming.getAppId());
        c.setSdkAppId(incoming.getSdkAppId());
        c.setAppKey(keepExistingWhenMaskedOrBlank(incoming.getAppKey(), existing != null ? existing.getAppKey() : null));
        c.setAppSecret(keepExistingWhenMaskedOrBlank(incoming.getAppSecret(), existing != null ? existing.getAppSecret() : null));
        c.setSender(incoming.getSender());
        c.setTemplateId(incoming.getTemplateId());
        c.setEndpoint(incoming.getEndpoint());
        c.setRegion(incoming.getRegion());
        c.setDailyLimit(incoming.getDailyLimit());
        c.setRateLimitPerMinute(incoming.getRateLimitPerMinute());
        c.setEnabled(incoming.getEnabled());
        return c;
    }

    private EmailConfigDto mergeEmailConfig(String platformType, EmailConfigDto incoming) {
        EmailConfigDto existing = parseExistingConfig(platformType, EmailConfigDto.class);
        EmailConfigDto c = new EmailConfigDto();
        c.setHost(incoming.getHost());
        c.setPort(incoming.getPort());
        c.setUsername(keepExistingWhenMaskedOrBlank(incoming.getUsername(), existing != null ? existing.getUsername() : null));
        c.setPassword(keepExistingWhenMaskedOrBlank(incoming.getPassword(), existing != null ? existing.getPassword() : null));
        c.setFromAddress(incoming.getFromAddress());
        c.setFromName(incoming.getFromName());
        c.setSsl(incoming.getSsl());
        c.setTls(incoming.getTls());
        c.setEncoding(incoming.getEncoding());
        c.setEnabled(incoming.getEnabled());
        return c;
    }

    private EncryptionConfigDto mergeEncryptionConfig(String platformType, EncryptionConfigDto incoming) {
        EncryptionConfigDto existing = parseExistingConfig(platformType, EncryptionConfigDto.class);
        EncryptionConfigDto c = new EncryptionConfigDto();
        c.setAesKey(keepExistingWhenMaskedOrBlank(incoming.getAesKey(), existing != null ? existing.getAesKey() : null));
        c.setSm4Key(keepExistingWhenMaskedOrBlank(incoming.getSm4Key(), existing != null ? existing.getSm4Key() : null));
        c.setAlgorithm(incoming.getAlgorithm());
        c.setKeyDerivation(incoming.getKeyDerivation());
        c.setKeyRotationDays(incoming.getKeyRotationDays());
        c.setEnabled(incoming.getEnabled());
        return c;
    }

    private <T> T parseExistingConfig(String platformType, Class<T> clazz) {
        return parseConfig(integrationConfigService.getRawConfig(platformType), clazz);
    }

    private String keepExistingWhenMaskedOrBlank(String incoming, String existing) {
        if (!StringUtils.hasText(incoming) || isMaskedPlaceholder(incoming)) {
            return existing;
        }
        return incoming;
    }

    private boolean isMaskedPlaceholder(String value) {
        return StringUtils.hasText(value) && value.trim().startsWith("***");
    }

    private Object buildMaskedConfig(String platformType, IntegrationConfig cfg) throws Exception {
        switch (platformType) {
            case "wechat":
                return mask(parseConfig(cfg, WechatConfigDto.class));
            case "dingtalk":
                return mask(parseConfig(cfg, DingTalkConfigDto.class));
            case "feishu":
                return mask(parseConfig(cfg, FeishuConfigDto.class));
            case "alipay":
                return mask(parseConfig(cfg, AlipayConfigDto.class));
            case "yunzhanghu":
                return mask(parseConfig(cfg, YunzhanghuConfigDto.class));
            case "sms":
                return mask(parseConfig(cfg, SmsConfigDto.class));
            case "email":
                return mask(parseConfig(cfg, EmailConfigDto.class));
            case "encryption":
                return mask(parseConfig(cfg, EncryptionConfigDto.class));
            default:
                throw new IllegalArgumentException("不支持的平台类型: " + platformType);
        }
    }

    private <T> T parseConfig(IntegrationConfig cfg, Class<T> clazz) {
        if (cfg == null || !StringUtils.hasText(cfg.getConfigJson())) {
            return null;
        }
        String raw = cfg.getConfigJson().trim();
        try {
            String plain = configDecryptionService.decrypt(raw);
            if (looksLikeJson(plain)) {
                return objectMapper.readValue(plain, clazz);
            }
            // 兼容历史异常数据：曾出现过重复加密场景，这里做一次兜底解密
            String twicePlain = configDecryptionService.decrypt(plain);
            if (looksLikeJson(twicePlain)) {
                log.warn("检测到{}存在历史重复加密，已按兼容逻辑解析", cfg.getPlatformType());
                return objectMapper.readValue(twicePlain, clazz);
            }
            log.warn("解析{}配置失败(解密后非JSON)", cfg.getPlatformType());
            return null;
        } catch (Exception decryptEx) {
            if (looksLikeJson(raw)) {
                try {
                    return objectMapper.readValue(raw, clazz);
                } catch (Exception parseEx) {
                    log.warn("解析{}配置失败(明文JSON解析异常): {}", cfg.getPlatformType(), parseEx.getMessage());
                    return null;
                }
            }
            log.warn("解析{}配置失败(解密/明文均失败): {}", cfg.getPlatformType(), decryptEx.getMessage());
            return null;
        }
    }

    private boolean looksLikeJson(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    // 脱敏返回，避免在前端泄露敏感数据
    private WechatConfigDto mask(WechatConfigDto dto) {
        if (dto == null) return null;
        WechatConfigDto c = new WechatConfigDto();
        c.setCorpId(maskKeepTail(dto.getCorpId()));
        c.setCorpSecret(maskAll(dto.getCorpSecret()));
        c.setAgentId(dto.getAgentId());
        return c;
    }

    private DingTalkConfigDto mask(DingTalkConfigDto dto) {
        if (dto == null) return null;
        DingTalkConfigDto c = new DingTalkConfigDto();
        c.setAppKey(maskKeepTail(dto.getAppKey()));
        c.setAppSecret(maskAll(dto.getAppSecret()));
        return c;
    }

    private FeishuConfigDto mask(FeishuConfigDto dto) {
        if (dto == null) return null;
        FeishuConfigDto c = new FeishuConfigDto();
        c.setAppId(maskKeepTail(dto.getAppId()));
        c.setAppSecret(maskAll(dto.getAppSecret()));
        return c;
    }

    private AlipayConfigDto mask(AlipayConfigDto dto) {
        if (dto == null) return null;
        AlipayConfigDto c = new AlipayConfigDto();
        c.setAppId(maskKeepTail(dto.getAppId()));
        c.setServerUrl(dto.getServerUrl());
        c.setPrivateKey(maskAll(dto.getPrivateKey()));
        c.setPublicKey(maskKeepTail(dto.getPublicKey()));
        c.setCharset(dto.getCharset());
        c.setSignType(dto.getSignType());
        c.setFormat(dto.getFormat());
        c.setNotifyUrl(dto.getNotifyUrl());
        c.setReturnUrl(dto.getReturnUrl());

        // 新增字段脱敏
        c.setCertMode(dto.getCertMode());
        c.setAppCertPath(dto.getAppCertPath()); // 路径不脱敏，需要显示
        c.setAlipayCertPath(dto.getAlipayCertPath()); // 路径不脱敏
        c.setAlipayRootCertPath(dto.getAlipayRootCertPath()); // 路径不脱敏
        c.setConnectTimeout(dto.getConnectTimeout());
        c.setReadTimeout(dto.getReadTimeout());
        c.setSingleLimit(dto.getSingleLimit());
        c.setDailyLimit(dto.getDailyLimit());
        c.setRealNameVerify(dto.getRealNameVerify());

        // 接口内容加密配置（AES加密密钥需要脱敏）
        c.setEncryptKey(maskAll(dto.getEncryptKey())); // AES密钥脱敏
        c.setEncryptType(dto.getEncryptType()); // 加密类型不脱敏

        return c;
    }

    private String maskAll(String s) {
        if (s == null || s.isBlank()) return s;
        return "******";
    }

    private String maskKeepTail(String s) {
        if (s == null || s.isBlank()) return s;
        int len = s.length();
        if (len <= 4) return "****";
        return "***" + s.substring(len - 4);
    }

    private YunzhanghuConfigDto mask(YunzhanghuConfigDto dto) {
        if (dto == null) return null;
        YunzhanghuConfigDto c = new YunzhanghuConfigDto();
        c.setDealerId(maskKeepTail(dto.getDealerId()));
        c.setBrokerId(maskKeepTail(dto.getBrokerId()));
        c.setAppKey(maskKeepTail(dto.getAppKey()));
        c.setDes3Key(maskAll(dto.getDes3Key()));
        c.setRsaPrivateKey(maskAll(dto.getRsaPrivateKey()));
        c.setRsaPublicKey(maskAll(dto.getRsaPublicKey()));
        c.setSignType(dto.getSignType());
        c.setUrl(dto.getUrl());
        c.setNotifyUrl(dto.getNotifyUrl());
        c.setProjectId(dto.getProjectId());
        c.setCheckName(dto.getCheckName());
        c.setDealerPlatformName(dto.getDealerPlatformName());
        c.setIsDebug(dto.getIsDebug());
        return c;
    }

    // 新增的脱敏方法
    private SmsConfigDto mask(SmsConfigDto dto) {
        if (dto == null) return null;
        SmsConfigDto c = new SmsConfigDto();
        c.setProvider(dto.getProvider());
        c.setAccessKeyId(maskKeepTail(dto.getAccessKeyId()));
        c.setAccessKeySecret(maskAll(dto.getAccessKeySecret()));
        c.setSignName(dto.getSignName());
        c.setTemplateCode(dto.getTemplateCode());
        c.setSecretId(maskKeepTail(dto.getSecretId()));
        c.setSecretKey(maskAll(dto.getSecretKey()));
        c.setAppId(dto.getAppId());
        c.setSdkAppId(dto.getSdkAppId());
        c.setAppKey(maskKeepTail(dto.getAppKey()));
        c.setAppSecret(maskAll(dto.getAppSecret()));
        c.setSender(dto.getSender());
        c.setTemplateId(dto.getTemplateId());
        c.setEndpoint(dto.getEndpoint());
        c.setRegion(dto.getRegion());
        c.setDailyLimit(dto.getDailyLimit());
        c.setRateLimitPerMinute(dto.getRateLimitPerMinute());
        c.setEnabled(dto.getEnabled());
        return c;
    }

    private EmailConfigDto mask(EmailConfigDto dto) {
        if (dto == null) return null;
        EmailConfigDto c = new EmailConfigDto();
        c.setHost(dto.getHost());
        c.setPort(dto.getPort());
        c.setUsername(maskKeepTail(dto.getUsername()));
        c.setPassword(maskAll(dto.getPassword()));
        c.setFromAddress(dto.getFromAddress());
        c.setFromName(dto.getFromName());
        c.setSsl(dto.getSsl());
        c.setTls(dto.getTls());
        c.setEncoding(dto.getEncoding());
        c.setEnabled(dto.getEnabled());
        return c;
    }

    private EncryptionConfigDto mask(EncryptionConfigDto dto) {
        if (dto == null) return null;
        EncryptionConfigDto c = new EncryptionConfigDto();
        c.setAesKey(maskAll(dto.getAesKey()));
        c.setSm4Key(maskAll(dto.getSm4Key()));
        c.setAlgorithm(dto.getAlgorithm());
        c.setKeyDerivation(dto.getKeyDerivation());
        c.setKeyRotationDays(dto.getKeyRotationDays());
        c.setEnabled(dto.getEnabled());
        return c;
    }

    // 辅助方法
    private String getPlatformDisplayName(String platformType) {
        switch (platformType) {
            case "wechat": return "企业微信";
            case "dingtalk": return "钉钉";
            case "feishu": return "飞书";
            case "alipay": return "支付宝";
            case "yunzhanghu": return "云账户";
            case "sms": return "短信服务";
            case "email": return "邮件服务";
            case "encryption": return "加密配置";
            default: return platformType;
        }
    }

    private String testPlatformConnection(String platformType) {
        try {
            if (!integrationConfigService.isPlatformEnabled(platformType)) {
                return "disconnected";
            }
            boolean connected = false;
            switch (platformType) {
                case "wechat":
                case "dingtalk":
                case "feishu":
                    connected = organizationSyncService.checkPlatformConnection(platformType);
                    break;
                case "alipay":
                    connected = alipayService.checkAlipayConnection();
                    break;
                case "yunzhanghu":
                    connected = yunzhanghuClient.healthCheck();
                    break;
                case "sms":
                    // SMS通常不需要连接测试，检查配置是否完整即可
                    connected = integrationConfigService.isPlatformEnabled("sms");
                    break;
                case "email":
                    // Email可以检查配置是否完整
                    connected = integrationConfigService.isPlatformEnabled("email");
                    break;
                case "encryption":
                    connected = encryptionService.checkEncryptionConfig();
                    break;
                default:
                    return "unknown";
            }
            return connected ? "connected" : "disconnected";
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            log.warn("测试{}连接时发生异常", platformType, e);
            return "unknown";
        }
    }

    /**
     * 上传支付宝证书文件
     *
     * @param file 证书文件（.crt 格式）
     * @param certType 证书类型：appCert, alipayCert, alipayRootCert
     * @return 证书文件在服务器上的绝对路径
     */
    @PostMapping("/alipay/cert-upload")
    public ApiResponse<String> uploadAlipayCert(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam("certType") String certType,
            jakarta.servlet.http.HttpServletRequest request) {

        try {
            // 验证证书类型
            if (!java.util.Set.of("appCert", "alipayCert", "alipayRootCert").contains(certType)) {
                return ApiResponse.error("非法的证书类型: " + certType);
            }

            // 验证文件格式
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase(java.util.Locale.ROOT).endsWith(".crt")) {
                return ApiResponse.error("证书文件必须是 .crt 格式");
            }

            if (file.isEmpty()) {
                return ApiResponse.error("证书文件不能为空");
            }

            // 验证文件大小（最大 1MB）
            if (file.getSize() > 1024 * 1024) {
                return ApiResponse.error("证书文件大小不能超过 1MB");
            }

            byte[] certContent = file.getBytes();
            if (!isValidX509Certificate(certContent)) {
                return ApiResponse.error("证书文件内容不是有效的 X.509 证书");
            }

            // 生成固定格式的文件名
            String fileName = certType + ".crt";

            // 直接存储到固定目录 certs/alipay/ 下，不按日期分目录
            java.nio.file.Path rootPath = java.nio.file.Paths.get(fileStorageProperties.getLocal().getBasePath())
                    .toAbsolutePath()
                    .normalize();
            String relativePath = "certs/alipay/" + fileName;
            java.nio.file.Path destinationFile = rootPath.resolve(relativePath).normalize();

            // 安全检查：确保路径在根目录下
            if (!destinationFile.startsWith(rootPath)) {
                return ApiResponse.error("非法文件路径");
            }

            // 创建目录
            java.nio.file.Files.createDirectories(destinationFile.getParent());

            // 保存文件
            java.nio.file.Files.write(destinationFile, certContent,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE);

            // 返回绝对路径
            String absolutePath = destinationFile.toAbsolutePath().toString();

            auditSafe("ALIPAY_CERT_UPLOAD", certType, request, true, "文件大小: " + file.getSize());
            log.info("支付宝证书上传成功: certType={}, path={}", certType, absolutePath);

            return ApiResponse.success(absolutePath);

        } catch (Exception e) {
            log.error("证书上传失败: certType={}", certType, e);
            auditSafe("ALIPAY_CERT_UPLOAD", certType, request, false, e.getMessage());
            return ApiResponse.error("证书上传失败: " + e.getMessage());
        }
    }

    private boolean isValidX509Certificate(byte[] certContent) {
        if (certContent == null || certContent.length == 0) {
            return false;
        }
        try (java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(certContent)) {
            java.security.cert.CertificateFactory certificateFactory =
                    java.security.cert.CertificateFactory.getInstance("X.509");
            java.util.Collection<? extends java.security.cert.Certificate> certificates =
                    certificateFactory.generateCertificates(inputStream);
            return certificates != null && !certificates.isEmpty();
        } catch (java.security.cert.CertificateException e) {
            return false;
        } catch (java.io.IOException e) {
            return false;
        }
    }
}
