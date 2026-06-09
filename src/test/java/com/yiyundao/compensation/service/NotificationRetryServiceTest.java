package com.yiyundao.compensation.service;

import com.yiyundao.compensation.enums.NotificationStatus;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import com.yiyundao.compensation.modules.notification.service.NotificationRecordService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationRetryServiceTest {

    private final NotificationRecordService notificationRecordService = mock(NotificationRecordService.class);
    private final NotificationRetryExecutor retryExecutor = mock(NotificationRetryExecutor.class);
    private final NotificationRetryService service =
            new NotificationRetryService(notificationRecordService, retryExecutor);

    @Test
    void processRetryNotificationsShouldDispatchRecordsToExecutor() {
        NotificationRecord first = new NotificationRecord();
        first.setId(4001L);
        NotificationRecord second = new NotificationRecord();
        second.setId(4002L);
        when(notificationRecordService.getPendingRetryRecords()).thenReturn(List.of(first, second));

        service.processRetryNotifications();

        verify(retryExecutor).processRetryRecord(first);
        verify(retryExecutor).processRetryRecord(second);
    }

    @Test
    void manualRetryShouldDispatchToExecutorWhenClaimed() {
        NotificationRecord record = new NotificationRecord();
        record.setId(4003L);
        record.setStatus(NotificationStatus.FAILED);
        record.setRetryCount(2);
        record.setMaxRetry(3);
        record.setErrorMessage("failed");
        when(notificationRecordService.getById(4003L)).thenReturn(record);
        when(notificationRecordService.update(any())).thenReturn(true);

        service.manualRetry(4003L);

        assertThat(record.getStatus()).isEqualTo(NotificationStatus.RETRY);
        assertThat(record.getRetryCount()).isZero();
        assertThat(record.getNextRetryTime()).isNotNull();
        assertThat(record.getErrorMessage()).isNull();
        verify(retryExecutor).processRetryRecord(record);
    }

    @Test
    void manualRetryShouldSkipWhenClaimLost() {
        NotificationRecord record = new NotificationRecord();
        record.setId(4004L);
        record.setStatus(NotificationStatus.FAILED);
        record.setRetryCount(2);
        record.setMaxRetry(3);
        when(notificationRecordService.getById(4004L)).thenReturn(record);
        when(notificationRecordService.update(any())).thenReturn(false);

        service.manualRetry(4004L);

        verify(retryExecutor, never()).processRetryRecord(any());
        verify(notificationRecordService, never()).updateById(record);
    }

    @Test
    void cancelRetryShouldSkipWhenClaimLost() {
        NotificationRecord record = new NotificationRecord();
        record.setId(4005L);
        record.setStatus(NotificationStatus.SUCCESS);
        when(notificationRecordService.getById(4005L)).thenReturn(record);
        when(notificationRecordService.update(any())).thenReturn(false);

        service.cancelRetry(4005L);

        assertThat(record.getStatus()).isEqualTo(NotificationStatus.SUCCESS);
        verify(notificationRecordService, never()).updateById(record);
    }
}
