package com.yiyundao.compensation.interfaces.adapter.impl;

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
 * 钉钉通知适配器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DingTalkNotificationAdapter implements NotificationAdapter {

    private final WebClient webClient;
    private final IntegrationConfigService integrationConfigService;

    @Override
    public NotificationChannel getSupportedChannel() {
        return NotificationChannel.DINGTALK;
    }

    @Override
    public NotificationSendResult sendNotification(NotificationRecord record) {
        try {
            log.info("发送钉钉通知: recordId={}, recipientId={}", record.getId(), record.getRecipientId());

            // 获取access_token
            String accessToken = getAccessToken();
            if (accessToken == null) {
                return NotificationSendResult.failure("无法获取钉钉access_token");
            }

            // 构建消息体
            Map<String, Object> message = buildMessage(record);

            // 发送工作通知
            String url = "https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2?access_token=" + accessToken;

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                .uri(url)
                .bodyValue(message)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response != null && Integer.valueOf(0).equals(response.get("errcode"))) {
                return NotificationSendResult.success("0", "发送成功");
            } else if (response != null) {
                String errcode = String.valueOf(response.get("errcode"));
                String errmsg = String.valueOf(response.get("errmsg"));
                return NotificationSendResult.failure("钉钉发送失败: " + errcode + " - " + errmsg);
            } else {
                return NotificationSendResult.failure("钉钉发送失败: 响应为空");
            }

        } catch (Exception e) {
            log.error("钉钉通知发送异常: recordId={}", record.getId(), e);
            return NotificationSendResult.failure("发送异常: " + e.getMessage());
        }
    }

    @Override
    public boolean checkConnection() {
        try {
            // 检查配置是否存在且启用
            if (!integrationConfigService.isPlatformEnabled("dingtalk")) {
                return false;
            }

            Map<String, String> config = integrationConfigService.getDecryptedConfig("dingtalk");
            if (config == null || config.isEmpty()) {
                return false;
            }

            // 检查必需的配置项
            String appKey = config.get("appKey");
            String appSecret = config.get("appSecret");
            if (appKey == null || appSecret == null) {
                return false;
            }

            // 尝试获取access_token验证连接
            String accessToken = getAccessToken();
            return accessToken != null;
        } catch (Exception e) {
            log.error("钉钉连接检查失败", e);
            return false;
        }
    }

    /**
     * 获取access_token
     */
    private String getAccessToken() {
        try {
            Map<String, String> config = integrationConfigService.getDecryptedConfig("dingtalk");
            if (config == null) {
                log.warn("钉钉配置不存在");
                return null;
            }

            String appKey = config.get("appKey");
            String appSecret = config.get("appSecret");

            if (appKey == null || appSecret == null) {
                log.warn("钉钉配置不完整");
                return null;
            }

            String url = "https://oapi.dingtalk.com/gettoken?appkey=" + appKey + "&appsecret=" + appSecret;

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response != null && Integer.valueOf(0).equals(response.get("errcode"))) {
                return String.valueOf(response.get("access_token"));
            } else {
                log.error("获取钉钉access_token失败: {}", response);
                return null;
            }

        } catch (Exception e) {
            log.error("获取钉钉access_token异常", e);
            return null;
        }
    }

    /**
     * 构建消息体
     */
    private Map<String, Object> buildMessage(NotificationRecord record) {
        Map<String, Object> message = new HashMap<>();

        // 应用agentId
        Map<String, String> config = integrationConfigService.getDecryptedConfig("dingtalk");
        if (config != null && config.containsKey("agentId")) {
            message.put("agent_id", Long.parseLong(config.get("agentId")));
        } else {
            message.put("agent_id", 1L); // 默认应用ID
        }

        // 接收人
        message.put("userid_list", record.getRecipientId());

        // 消息体
        Map<String, Object> msg = new HashMap<>();
        msg.put("msgtype", "text");

        Map<String, String> text = new HashMap<>();
        String content = record.getTitle();
        if (record.getContent() != null) {
            content += "\n\n" + record.getContent();
        }
        text.put("content", content);
        msg.put("text", text);

        message.put("msg", msg);

        return message;
    }
}