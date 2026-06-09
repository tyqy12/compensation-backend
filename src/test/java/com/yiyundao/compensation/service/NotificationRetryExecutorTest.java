package com.yiyundao.compensation.service;

import com.yiyundao.compensation.enums.NotificationChannel;
import com.yiyundao.compensation.enums.NotificationStatus;
import com.yiyundao.compensation.interfaces.adapter.NotificationAdapter;
import com.yiyundao.compensation.interfaces.adapter.NotificationAdapter.NotificationSendResult;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import com.yiyundao.compensation.modules.notification.service.NotificationRecordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class NotificationRetryExecutorTest {

    private final NotificationRecordService notificationRecordService = mock(NotificationRecordService.class);
    private final NotificationAdapter systemAdapter = mock(NotificationAdapter.class);
    private final NotificationRouterService notificationRouterService = mock(NotificationRouterService.class);
    private final NotificationRetryExecutor executor =
            new NotificationRetryExecutor(
                    notificationRecordService,
                    Map.of(NotificationChannel.SYSTEM, systemAdapter),
                    notificationRouterService
            );

    @Test
    void processRetryRecordShouldTriggerConfiguredFallbacksWithoutSensitiveLogs(CapturedOutput output) {
        NotificationRecord record = new NotificationRecord();
        record.setId(4001L);
        record.setStatus(NotificationStatus.RETRY);
        record.setChannel(NotificationChannel.SYSTEM);
        record.setRecipientId("u-5001");
        record.setRetryCount(3);
        record.setMaxRetry(3);
        record.setTitle("六月工资条提醒");
        record.setContent("你的实发工资 18888.66 已到账");
        record.setFallbackChannels("[\"sms\",\"system\"]");

        when(notificationRecordService.update(any())).thenReturn(true);

        executor.processRetryRecord(record);

        assertThat(output).contains("recordId=4001");
        assertThat(output).doesNotContain("六月工资条提醒");
        assertThat(output).doesNotContain("18888.66");
        verify(notificationRouterService).createAndSendFallbackNotification(record, NotificationChannel.SMS);
        verify(notificationRouterService).createAndSendFallbackNotification(record, NotificationChannel.SYSTEM);
        verify(notificationRouterService, never()).createAndSendFallbackNotification(record, NotificationChannel.EMAIL);
        verify(notificationRecordService).update(any());
    }

    @Test
    void processRetryRecordShouldSkipFallbackWhenExhaustedClaimLost() {
        NotificationRecord record = new NotificationRecord();
        record.setId(4004L);
        record.setStatus(NotificationStatus.RETRY);
        record.setChannel(NotificationChannel.SYSTEM);
        record.setRecipientId("u-5004");
        record.setRetryCount(3);
        record.setMaxRetry(3);
        record.setFallbackChannels("[\"sms\"]");

        when(notificationRecordService.update(any())).thenReturn(false);

        executor.processRetryRecord(record);

        verify(notificationRouterService, never()).createAndSendFallbackNotification(any(), any());
        verify(notificationRecordService, never()).updateById(record);
    }

    @Test
    void processRetryRecordShouldUseRealAdapterAndMarkSuccess() {
        NotificationRecord record = new NotificationRecord();
        record.setId(4002L);
        record.setStatus(NotificationStatus.RETRY);
        record.setChannel(NotificationChannel.SYSTEM);
        record.setRecipientId("u-5002");
        record.setRetryCount(0);
        record.setMaxRetry(3);
        record.setTitle("提醒");
        record.setContent("请处理");

        when(notificationRecordService.update(any())).thenReturn(true);
        when(systemAdapter.sendNotification(record)).thenReturn(NotificationSendResult.success("SYSTEM", "ok"));

        executor.processRetryRecord(record);

        verify(systemAdapter).sendNotification(eq(record));
        assertThat(record.getStatus()).isEqualTo(NotificationStatus.SUCCESS);
        assertThat(record.getResponseCode()).isEqualTo("SYSTEM");
        assertThat(record.getResponseMessage()).isEqualTo("ok");
        assertThat(record.getErrorMessage()).isNull();
        verify(notificationRecordService, times(2)).update(any());
        verify(notificationRecordService, never()).updateById(record);
    }

    @Test
    void processRetryRecordShouldSendFinalAllowedRetryBeforeFallback() {
        NotificationRecord record = new NotificationRecord();
        record.setId(4007L);
        record.setStatus(NotificationStatus.RETRY);
        record.setChannel(NotificationChannel.SYSTEM);
        record.setRecipientId("u-5007");
        record.setRetryCount(0);
        record.setMaxRetry(1);
        record.setFallbackChannels("[\"sms\"]");

        when(notificationRecordService.update(any())).thenReturn(true);
        when(systemAdapter.sendNotification(record)).thenReturn(NotificationSendResult.failure("provider failed"));

        executor.processRetryRecord(record);

        verify(systemAdapter).sendNotification(eq(record));
        assertThat(record.getRetryCount()).isEqualTo(1);
        assertThat(record.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(record.getNextRetryTime()).isNull();
        verify(notificationRouterService).createAndSendFallbackNotification(record, NotificationChannel.SMS);
        verify(notificationRecordService, times(2)).update(any());
    }

    @Test
    void processRetryRecordShouldNotOverwriteWhenFinalUpdateLost() {
        NotificationRecord record = new NotificationRecord();
        record.setId(4006L);
        record.setStatus(NotificationStatus.RETRY);
        record.setChannel(NotificationChannel.SYSTEM);
        record.setRecipientId("u-5006");
        record.setRetryCount(0);
        record.setMaxRetry(3);

        when(notificationRecordService.update(any())).thenReturn(true, false);
        when(systemAdapter.sendNotification(record)).thenReturn(NotificationSendResult.success("SYSTEM", "ok"));

        executor.processRetryRecord(record);

        verify(systemAdapter).sendNotification(eq(record));
        assertThat(record.getStatus()).isEqualTo(NotificationStatus.SUCCESS);
        verify(notificationRecordService, times(2)).update(any());
        verify(notificationRecordService, never()).updateById(record);
    }

    @Test
    void processRetryRecordShouldUseDefaultMaxRetryWhenMissing() {
        NotificationRecord record = new NotificationRecord();
        record.setId(4005L);
        record.setStatus(NotificationStatus.RETRY);
        record.setChannel(NotificationChannel.SYSTEM);
        record.setRecipientId("u-5005");
        record.setRetryCount(null);
        record.setMaxRetry(null);

        when(notificationRecordService.update(any())).thenReturn(true);
        when(systemAdapter.sendNotification(record)).thenReturn(NotificationSendResult.success("SYSTEM", "ok"));

        executor.processRetryRecord(record);

        verify(systemAdapter).sendNotification(eq(record));
        assertThat(record.getRetryCount()).isEqualTo(1);
        assertThat(record.getStatus()).isEqualTo(NotificationStatus.SUCCESS);
        verify(notificationRouterService, never()).createAndSendFallbackNotification(any(), any());
    }

    @Test
    void processRetryRecordShouldSkipWhenClaimLost() {
        NotificationRecord record = new NotificationRecord();
        record.setId(4003L);
        record.setStatus(NotificationStatus.RETRY);
        record.setChannel(NotificationChannel.SYSTEM);
        record.setRecipientId("u-5003");
        record.setRetryCount(0);
        record.setMaxRetry(3);

        when(notificationRecordService.update(any())).thenReturn(false);

        executor.processRetryRecord(record);

        verify(systemAdapter, never()).sendNotification(any());
        verify(notificationRecordService, never()).updateById(record);
    }
}
