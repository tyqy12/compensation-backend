package com.yiyundao.compensation.interfaces.adapter.impl;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.annotation.SensitiveType;
import com.yiyundao.compensation.common.utils.SecretLogSanitizer;
import com.yiyundao.compensation.enums.NotificationChannel;
import com.yiyundao.compensation.interfaces.adapter.NotificationAdapter;
import com.yiyundao.compensation.interfaces.dto.config.SmsConfigDto;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 短信通知适配器 - 支持阿里云、腾讯云、华为云
 * 使用各云厂商的REST API + OkHttp
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsNotificationAdapter implements NotificationAdapter {

    private static final Pattern CHINA_PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");

    private final IntegrationConfigService integrationConfigService;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public NotificationChannel getSupportedChannel() {
        return NotificationChannel.SMS;
    }

    @Override
    public NotificationSendResult sendNotification(NotificationRecord record) {
        if (record == null) {
            return NotificationSendResult.failure("通知记录不能为空");
        }
        try {
            log.info("发送短信通知: recordId={}, phone={}", record.getId(), maskPhone(record.getRecipientId()));

            // 验证手机号格式
            String phone = record.getRecipientId();
            if (!isValidPhoneNumber(phone)) {
                return NotificationSendResult.failure("无效的手机号格式: " + maskPhone(phone));
            }

            // 构建短信内容
            String smsContent = buildSmsContent(record);

            // 发送短信
            boolean success = sendSmsMessage(phone, smsContent, record.getBusinessKey());

            if (success) {
                return NotificationSendResult.success("200", "短信发送成功");
            } else {
                return NotificationSendResult.failure("短信发送失败");
            }

        } catch (Exception e) {
            log.error("短信通知发送异常: recordId={}, errorType={}, error={}",
                    record.getId(), e.getClass().getSimpleName(), sanitizeForSmsLog(e));
            return NotificationSendResult.failure("发送异常: " + sanitizeForSmsLog(e));
        }
    }

    @Override
    public boolean checkConnection() {
        try {
            // 检查短信配置是否存在且启用
            SmsConfigDto smsConfig = integrationConfigService.getSmsConfig();
            if (smsConfig == null || !Boolean.TRUE.equals(smsConfig.getEnabled())) {
                return false;
            }

            // 检查必需的配置项
            String provider = smsConfig.getProvider();
            if (provider == null || provider.trim().isEmpty()) {
                return false;
            }

            // 根据服务商检查配置完整性
            switch (provider.toLowerCase()) {
                case "aliyun":
                    return smsConfig.getAccessKeyId() != null &&
                           smsConfig.getAccessKeySecret() != null &&
                           smsConfig.getSignName() != null;
                case "tencent":
                    return smsConfig.getSecretId() != null &&
                           smsConfig.getSecretKey() != null &&
                           smsConfig.getAppId() != null;
                case "huawei":
                    return smsConfig.getAppKey() != null &&
                           smsConfig.getAppSecret() != null &&
                           smsConfig.getSender() != null;
                case "mock":
                    return true; // 模拟发送不需要额外配置
                default:
                    return false;
            }

        } catch (Exception e) {
            log.error("短信服务连接检查异常: errorType={}, error={}",
                    e.getClass().getSimpleName(), sanitizeForSmsLog(e));
            return false;
        }
    }

    /**
     * 验证手机号格式
     */
    private boolean isValidPhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }

        // 简单的中国手机号验证
        return phone.matches("^1[3-9]\\d{9}$");
    }

    /**
     * 构建短信内容
     */
    private String buildSmsContent(NotificationRecord record) {
        StringBuilder content = new StringBuilder();

        // 短信通常有长度限制，需要简化内容
        content.append("【薪酬助手】");

        String title = record.getTitle();
        if (title != null && title.length() > 50) {
            title = title.substring(0, 50) + "...";
        }
        content.append(title);

        // 如果有业务关键字，可以添加
        if (record.getBusinessKey() != null) {
            content.append("(").append(record.getBusinessKey()).append(")");
        }

        return content.toString();
    }

    /**
     * 发送短信消息
     */
    private boolean sendSmsMessage(String phone, String content, String templateCode) {
        try {
            // 获取短信配置
            SmsConfigDto smsConfig = integrationConfigService.getSmsConfig();
            if (smsConfig == null || !Boolean.TRUE.equals(smsConfig.getEnabled())) {
                log.warn("短信配置未启用或不存在");
                return false;
            }

            String provider = smsConfig.getProvider();
            if (provider == null) {
                log.warn("短信服务商未配置");
                return false;
            }

            // 如果没有指定模板，使用配置中的默认模板
            if (templateCode == null || templateCode.isBlank()) {
                templateCode = smsConfig.getTemplateCode();
            }

            // 根据服务商类型发送短信
            switch (provider.toLowerCase()) {
                case "aliyun":
                    return sendAliyunSms(phone, content, smsConfig, templateCode);
                case "tencent":
                    return sendTencentSms(phone, content, smsConfig, templateCode);
                case "huawei":
                    return sendHuaweiSms(phone, content, smsConfig, templateCode);
                case "mock":
                    // 模拟发送（开发环境）
                    log.info("模拟发送短信: phone={}, contentLength={}",
                            maskPhone(phone), content != null ? content.length() : 0);
                    return Math.random() > 0.1;
                default:
                    log.error("不支持的短信服务商: {}", provider);
                    return false;
            }

        } catch (Exception e) {
            log.error("短信发送异常: errorType={}, error={}",
                    e.getClass().getSimpleName(), sanitizeForSmsLog(e));
            return false;
        }
    }

    /**
     * 阿里云短信发送
     */
    private boolean sendAliyunSms(String phone, String content, SmsConfigDto config, String templateCode) {
        try {
            log.info("使用阿里云发送短信: phone={}, signName={}", maskPhone(phone), config.getSignName());

            // 初始化客户端
            Config alicloudConfig = new Config()
                    .setAccessKeyId(config.getAccessKeyId())
                    .setAccessKeySecret(config.getAccessKeySecret());
            // 阿里云短信服务默认Endpoint
            alicloudConfig.endpoint = "dysmsapi.aliyuncs.com";

            Client client = new Client(alicloudConfig);

            // 构建请求
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(config.getSignName())
                    .setTemplateCode(templateCode != null ? templateCode : "SMS_000000000");

            // 设置模板参数（如果模板需要参数）
            if (content != null && !content.isEmpty()) {
                Map<String, String> templateParam = new HashMap<>();
                templateParam.put("content", content);
                request.setTemplateParam(com.aliyun.teautil.Common.toJSONString(templateParam));
            }

            // 发送请求
            SendSmsResponse response = client.sendSms(request);

            if (response != null && "OK".equals(response.getBody().getCode())) {
                log.info("阿里云短信发送成功: bizId={}", response.getBody().getBizId());
                return true;
            } else {
                log.error("阿里云短信发送失败: code={}, msg={}",
                        response != null ? response.getBody().getCode() : "null",
                        response != null ? response.getBody().getMessage() : "null");
                return false;
            }

        } catch (TeaException e) {
            log.error("阿里云短信发送失败 (TeaException): code={}, msg={}",
                    e.getCode(), sanitizeForSmsLog(e.getMessage()));
            return false;
        } catch (Exception e) {
            log.error("阿里云短信发送异常: errorType={}, error={}",
                    e.getClass().getSimpleName(), sanitizeForSmsLog(e));
            return false;
        }
    }

    /**
     * 腾讯云短信发送（REST API + OkHttp）
     */
    private boolean sendTencentSms(String phone, String content, SmsConfigDto config, String templateCode) {
        try {
            log.info("使用腾讯云发送短信: phone={}, appId={}", maskPhone(phone), config.getAppId());

            // 腾讯云短信API地址
            String endpoint = "https://sms.tencentcloudapi.com";
            String region = config.getRegion() != null ? config.getRegion() : "ap-guangzhou";

            // 构建请求参数
            Map<String, Object> params = new HashMap<>();
            params.put("PhoneNumberSet", new String[]{phone});
            params.put("SmsSdkAppId", config.getAppId());
            params.put("TemplateId", templateCode != null ? templateCode : "000000000");
            params.put("TemplateParamSet", new String[]{content});
            params.put("SignName", config.getSignName() != null ? config.getSignName() : "");
            params.put("SessionContext", "compensation_system");

            // 生成时间戳和随机数
            long timestamp = System.currentTimeMillis() / 1000;
            String nonce = String.valueOf((int) (Math.random() * 1000000));

            // 构建签名字符串
            String signature = generateTencentSignature(
                    config.getSecretId(),
                    config.getSecretKey(),
                    "sms",
                    "SendSms",
                    region,
                    timestamp,
                    nonce,
                    params
            );

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("Action", "SendSms");
            requestBody.put("Version", "2021-01-11");
            requestBody.put("Region", region);
            requestBody.put("Timestamp", timestamp);
            requestBody.put("Nonce", nonce);
            requestBody.put("SecretId", config.getSecretId());
            requestBody.put("Signature", signature);
            requestBody.putAll(params);

            // 发送HTTP请求
            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(requestBody),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String respBody = response.body().string();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = objectMapper.readValue(respBody, Map.class);

                    if (result.containsKey("Response")) {
                        Object respObj = result.get("Response");
                        if (respObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> resp = (Map<String, Object>) respObj;
                            Object sendStatus = resp.get("SendStatusSet");
                            if (sendStatus instanceof String && "Ok".equals(sendStatus)) {
                                log.info("腾讯云短信发送成功");
                                return true;
                            } else {
                                log.error("腾讯云短信发送失败: {}", sanitizeForSmsLog(resp));
                            }
                        }
                    }
                }
            }

            return false;

        } catch (Exception e) {
            log.error("腾讯云短信发送异常: errorType={}, error={}",
                    e.getClass().getSimpleName(), sanitizeForSmsLog(e));
            return false;
        }
    }

    /**
     * 生成腾讯云API签名
     */
    private String generateTencentSignature(String secretId, String secretKey, String service,
                                            String action, String region, long timestamp,
                                            String nonce, Map<String, Object> params) {
        try {
            // 构建签名字符串
            StringBuilder signedHeaders = new StringBuilder();
            signedHeaders.append("content-type;host");

            String payload = objectMapper.writeValueAsString(params);

            String canonicalHeaders = "content-type:application/json\nhost:sms.tencentcloudapi.com\n";
            String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\ncontent-type,host\n" +
                    org.apache.commons.codec.digest.DigestUtils.sha256Hex(payload);

            String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" +
                    timestamp + "/" + service + "/tc3_request\n" +
                    org.apache.commons.codec.digest.DigestUtils.sha256Hex(canonicalRequest);

            // 计算签名（简化版本，实际需要更复杂的HMAC计算）
            // 这里使用简单的secretKey签名作为占位符
            return org.apache.commons.codec.binary.Base64.encodeBase64String(
                    (secretKey + stringToSign).getBytes()
            );

        } catch (Exception e) {
            log.error("生成签名失败: errorType={}, error={}",
                    e.getClass().getSimpleName(), sanitizeForSmsLog(e));
            return "";
        }
    }

    /**
     * 华为云短信发送（REST API + OkHttp）
     */
    private boolean sendHuaweiSms(String phone, String content, SmsConfigDto config, String templateCode) {
        try {
            log.info("使用华为云发送短信: phone={}, sender={}", maskPhone(phone), config.getSender());

            // 华为云短信API地址
            String endpoint = config.getEndpoint() != null ?
                    config.getEndpoint() : "https://rtcsms.cn-north-4.myhuaweicloud.com:10443";

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("sender", config.getSender());
            requestBody.put("receiver", phone);
            requestBody.put("templateId", templateCode != null ? templateCode : "000000000");
            requestBody.put("templateParas", new String[]{content});

            // 发送HTTP请求
            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(requestBody),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(endpoint + "/sms/batchSendSms/v1")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "WSSE realm=\"SDP\", profile=\"UsernameToken\", type=\"Appkey\"")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String respBody = response.body().string();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = objectMapper.readValue(respBody, Map.class);

                    // 华为云返回格式：{"result":"000000","desc":"success"}
                    Object resultCode = result.get("result");
                    if (resultCode instanceof String && "000000".equals(resultCode)) {
                        log.info("华为云短信发送成功");
                        return true;
                    } else {
                        log.error("华为云短信发送失败: {}", sanitizeForSmsLog(result));
                    }
                }
            }

            return false;

        } catch (Exception e) {
            log.error("华为云短信发送异常: errorType={}, error={}",
                    e.getClass().getSimpleName(), sanitizeForSmsLog(e));
            return false;
        }
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return phone;
        }
        String value = phone.trim();
        String digitsOnly = value.replaceAll("\\D", "");
        if (digitsOnly.matches("1[3-9]\\d{9}")) {
            return SensitiveType.PHONE.desensitize(digitsOnly);
        }
        return maskKeep(value, 3, 4);
    }

    private static String maskKeep(String value, int keepPrefix, int keepSuffix) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= keepPrefix + keepSuffix) {
            return "***";
        }
        return value.substring(0, keepPrefix)
                + "*".repeat(value.length() - keepPrefix - keepSuffix)
                + value.substring(value.length() - keepSuffix);
    }

    private static String sanitizeForSmsLog(Throwable throwable) {
        return sanitizeForSmsLog(SecretLogSanitizer.sanitize(throwable));
    }

    private static String sanitizeForSmsLog(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String sanitized = SecretLogSanitizer.sanitize(value);
        Matcher matcher = CHINA_PHONE_PATTERN.matcher(sanitized);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(maskPhone(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static Map<String, Object> sanitizeForSmsLog(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        value.forEach((key, item) -> sanitized.put(key, sanitizeForSmsLogValue(key, item)));
        return sanitized;
    }

    private static Object sanitizeForSmsLogValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        String normalizedKey = key == null ? "" : key.trim()
                .replace('-', '_')
                .replace('.', '_')
                .toLowerCase(java.util.Locale.ROOT);
        if (normalizedKey.contains("phone") || normalizedKey.contains("mobile") || normalizedKey.contains("receiver")) {
            return maskPhone(String.valueOf(value));
        }
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            nested.forEach((nestedKey, nestedValue) ->
                    normalized.put(String.valueOf(nestedKey), sanitizeForSmsLogValue(String.valueOf(nestedKey), nestedValue)));
            return normalized;
        }
        if (value instanceof CharSequence text) {
            return sanitizeForSmsLog(text.toString());
        }
        return value;
    }
}
