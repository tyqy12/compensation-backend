package com.yiyundao.compensation.interfaces.adapter.impl;

import com.yiyundao.compensation.common.utils.SecretLogSanitizer;
import com.yiyundao.compensation.enums.NotificationChannel;
import com.yiyundao.compensation.interfaces.adapter.NotificationAdapter;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信通知适配器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatNotificationAdapter implements NotificationAdapter {

    private final WebClient webClient;
    private final IntegrationConfigService integrationConfigService;

    @Override
    public NotificationChannel getSupportedChannel() {
        return NotificationChannel.WECHAT;
    }

    @Override
    public NotificationSendResult sendNotification(NotificationRecord record) {
        try {
            log.info("发送企业微信通知: recordId={}, recipientId={}", record.getId(), record.getRecipientId());

            // 获取access_token
            String accessToken = getAccessToken();
            if (accessToken == null) {
                return NotificationSendResult.failure("无法获取企业微信access_token");
            }

            // 构建消息体
            Map<String, Object> message = buildMessage(record);

            // 发送消息
            String url = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + accessToken;

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                .uri(url)
                .bodyValue(message)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response != null && "0".equals(String.valueOf(response.get("errcode")))) {
                return NotificationSendResult.success("0", "发送成功");
            } else if (response != null) {
                String errcode = String.valueOf(response.get("errcode"));
                String errmsg = String.valueOf(response.get("errmsg"));
                return NotificationSendResult.failure("企业微信发送失败: " + errcode + " - " + errmsg);
            } else {
                return NotificationSendResult.failure("企业微信发送失败: 响应为空");
            }

        } catch (Exception e) {
            log.error("企业微信通知发送异常: recordId={}, error={}", record.getId(), SecretLogSanitizer.sanitize(e));
            return NotificationSendResult.failure("发送异常: " + SecretLogSanitizer.sanitize(e));
        }
    }

    @Override
    public boolean checkConnection() {
        try {
            // 检查配置是否存在且完整
            if (!integrationConfigService.isPlatformEnabled("wechat")) {
                return false;
            }

            Map<String, String> config = integrationConfigService.getDecryptedConfig("wechat");
            if (config == null || config.isEmpty()) {
                return false;
            }

            // 检查必需的配置项
            String corpid = config.get("corpid");
            String corpsecret = config.get("corpsecret");
            if (corpid == null || corpsecret == null) {
                return false;
            }

            // 尝试获取access_token验证连接
            String accessToken = getAccessToken();
            return accessToken != null;
        } catch (Exception e) {
            log.error("企业微信连接检查失败: {}", SecretLogSanitizer.sanitize(e));
            return false;
        }
    }

    /**
     * 获取access_token
     */
    private String getAccessToken() {
        try {
            // 这里应该从集成配置服务获取企业微信配置
            // 简化实现，实际应该缓存token
            Map<String, String> config = integrationConfigService.getDecryptedConfig("wechat");
            if (config == null) {
                log.warn("企业微信配置不存在");
                return null;
            }

            String corpid = config.get("corpid");
            String corpsecret = config.get("corpsecret");

            if (corpid == null || corpsecret == null) {
                log.warn("企业微信配置不完整");
                return null;
            }

            String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + corpid + "&corpsecret=" + corpsecret;

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response != null && "0".equals(String.valueOf(response.get("errcode")))) {
                return String.valueOf(response.get("access_token"));
            } else {
                log.error("获取企业微信access_token失败: {}", SecretLogSanitizer.sanitize(response));
                return null;
            }

        } catch (Exception e) {
            log.error("获取企业微信access_token异常: {}", SecretLogSanitizer.sanitize(e));
            return null;
        }
    }

    /**
     * 构建消息体
     */
    private Map<String, Object> buildMessage(NotificationRecord record) {
        Map<String, Object> message = new HashMap<>();

        // 收件人
        message.put("touser", record.getRecipientId());

        // 消息类型
        message.put("msgtype", "text");

        // 应用ID (从配置获取)
        Map<String, String> config = integrationConfigService.getDecryptedConfig("wechat");
        if (config != null && config.containsKey("agentid")) {
            message.put("agentid", config.get("agentid"));
        } else {
            message.put("agentid", "1000002"); // 默认应用ID
        }

        // 文本内容
        Map<String, String> text = new HashMap<>();
        String content = record.getTitle();
        if (record.getContent() != null) {
            content += "\n\n" + record.getContent();
        }
        text.put("content", content);
        message.put("text", text);

        // 安全保密消息
        message.put("safe", 0);

        return message;
    }
}
