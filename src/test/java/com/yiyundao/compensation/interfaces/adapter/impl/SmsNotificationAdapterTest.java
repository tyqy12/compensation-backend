package com.yiyundao.compensation.interfaces.adapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.interfaces.adapter.NotificationAdapter.NotificationSendResult;
import com.yiyundao.compensation.interfaces.dto.config.SmsConfigDto;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class SmsNotificationAdapterTest {

    private final IntegrationConfigService integrationConfigService = mock(IntegrationConfigService.class);
    private final SmsNotificationAdapter adapter =
            new SmsNotificationAdapter(integrationConfigService, new ObjectMapper());

    @Test
    void sendNotificationShouldMaskInvalidPhoneInFailureMessageAndLogs(CapturedOutput output) {
        NotificationRecord record = new NotificationRecord();
        record.setId(1001L);
        record.setRecipientId("1380013");
        record.setTitle("工资条已生成");

        NotificationSendResult result = adapter.sendNotification(record);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("无效的手机号格式: ***");
        assertThat(result.getErrorMessage()).doesNotContain("1380013");
        assertThat(output).doesNotContain("phone=1380013");
        assertThat(output).contains("phone=***");
    }

    @Test
    void sendNotificationShouldMaskPhoneAndNotLogSmsContentWhenUsingMockProvider(CapturedOutput output) {
        SmsConfigDto config = new SmsConfigDto();
        config.setEnabled(true);
        config.setProvider("mock");
        when(integrationConfigService.getSmsConfig()).thenReturn(config);

        NotificationRecord record = new NotificationRecord();
        record.setId(1002L);
        record.setRecipientId("13800138000");
        record.setTitle("你的工资条验证码 654321");

        adapter.sendNotification(record);

        assertThat(output).contains("phone=138****8000");
        assertThat(output).doesNotContain("13800138000");
        assertThat(output).doesNotContain("654321");
        assertThat(output).contains("contentLength=");
    }

    @Test
    void sendNotificationShouldRejectNullRecord() {
        NotificationSendResult result = adapter.sendNotification(null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("通知记录不能为空");
    }
}
