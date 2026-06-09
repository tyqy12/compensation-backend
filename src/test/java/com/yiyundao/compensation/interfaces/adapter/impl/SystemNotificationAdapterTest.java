package com.yiyundao.compensation.interfaces.adapter.impl;

import com.yiyundao.compensation.interfaces.adapter.NotificationAdapter.NotificationSendResult;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class SystemNotificationAdapterTest {

    private final SystemNotificationAdapter adapter = new SystemNotificationAdapter();

    @Test
    void sendNotificationShouldNotLogTitleOrContent(CapturedOutput output) {
        NotificationRecord record = new NotificationRecord();
        record.setId(2001L);
        record.setRecipientId("user-1001");
        record.setTitle("工资条已生成");
        record.setContent("本月实发工资 123456.78，请及时确认");

        NotificationSendResult result = adapter.sendNotification(record);

        assertThat(result.isSuccess()).isTrue();
        assertThat(output).contains("recordId=2001");
        assertThat(output).contains("userId=user-1001");
        assertThat(output).contains("titleLength=");
        assertThat(output).contains("contentLength=");
        assertThat(output).doesNotContain("工资条已生成");
        assertThat(output).doesNotContain("123456.78");
    }

    @Test
    void sendNotificationShouldRejectNullRecord() {
        NotificationSendResult result = adapter.sendNotification(null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("通知记录不能为空");
    }
}
