package com.yiyundao.compensation.interfaces.adapter.impl;

import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DingTalkNotificationAdapterTest {

    @Mock
    private IntegrationConfigService integrationConfigService;

    @Test
    void buildMessageShouldFailClosedWhenAgentIdMissing() throws Exception {
        DingTalkNotificationAdapter adapter = new DingTalkNotificationAdapter(WebClient.builder().build(), integrationConfigService);
        when(integrationConfigService.getDecryptedConfig("dingtalk")).thenReturn(Map.of());

        Method method = DingTalkNotificationAdapter.class.getDeclaredMethod("buildMessage", NotificationRecord.class);
        method.setAccessible(true);

        assertThatThrownBy(() -> invoke(method, adapter, record()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agentId");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildMessageShouldUseConfiguredAgentId() throws Exception {
        DingTalkNotificationAdapter adapter = new DingTalkNotificationAdapter(WebClient.builder().build(), integrationConfigService);
        when(integrationConfigService.getDecryptedConfig("dingtalk")).thenReturn(Map.of("agentId", "123456"));

        Method method = DingTalkNotificationAdapter.class.getDeclaredMethod("buildMessage", NotificationRecord.class);
        method.setAccessible(true);

        Map<String, Object> message = (Map<String, Object>) method.invoke(adapter, record());

        assertThat(message.get("agent_id")).isEqualTo(123456L);
    }

    private static NotificationRecord record() {
        NotificationRecord record = new NotificationRecord();
        record.setId(1L);
        record.setRecipientId("user-1");
        record.setTitle("title");
        record.setContent("content");
        return record;
    }

    private static Object invoke(Method method, Object target, Object arg) throws Throwable {
        try {
            return method.invoke(target, arg);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
