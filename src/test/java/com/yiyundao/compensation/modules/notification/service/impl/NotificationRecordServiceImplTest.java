package com.yiyundao.compensation.modules.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.enums.NotificationStatus;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class NotificationRecordServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, NotificationRecord.class.getName());
        TableInfoHelper.initTableInfo(assistant, NotificationRecord.class);
    }

    @Test
    void pageNotificationRecordsShouldClampPageAndSize() {
        NotificationRecordServiceImpl service = spy(new NotificationRecordServiceImpl());
        doReturn(new Page<NotificationRecord>(1, 200, 0))
                .when(service).page(any(Page.class), any());

        Page<NotificationRecord> result = service.pageNotificationRecords(
                -1,
                1000,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(result.getCurrent()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(200);
    }

    @Test
    void getUserNotificationsShouldClampLimitBeforeAppendingSql() {
        NotificationRecordServiceImpl service = spy(new NotificationRecordServiceImpl());
        doReturn(List.of()).when(service).list(org.mockito.ArgumentMatchers.<Wrapper<NotificationRecord>>any());

        service.getUserNotifications("user-1", -10);
        service.getUserNotifications("user-1", 1000);

        org.mockito.ArgumentCaptor<Wrapper<NotificationRecord>> wrapperCaptor =
                org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(service, org.mockito.Mockito.times(2)).list(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getAllValues().get(0).getSqlSegment()).contains("limit 10");
        assertThat(wrapperCaptor.getAllValues().get(1).getSqlSegment()).contains("limit 200");
    }

    @Test
    void resendNotificationShouldQueueRetryableTerminalStates() {
        NotificationRecordServiceImpl service = spy(new NotificationRecordServiceImpl());
        NotificationRecord record = new NotificationRecord();
        record.setId(20L);
        record.setStatus(NotificationStatus.SUCCESS);
        doReturn(record).when(service).getById(20L);
        doReturn(false).when(service).update(org.mockito.ArgumentMatchers.<Wrapper<NotificationRecord>>any());

        service.resendNotification(20L);

        org.mockito.ArgumentCaptor<Wrapper<NotificationRecord>> wrapperCaptor =
                org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(service).update(wrapperCaptor.capture());
        LambdaUpdateWrapper<NotificationRecord> updateWrapper =
                (LambdaUpdateWrapper<NotificationRecord>) wrapperCaptor.getValue();
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("status IN");
        assertThat(updateWrapper.getParamNameValuePairs().values())
                .contains(
                        NotificationStatus.FAILED,
                        NotificationStatus.CANCELLED,
                        NotificationStatus.RETRY,
                        NotificationStatus.RETRY
                );
        assertThat(updateWrapper.getSqlSet())
                .contains("status")
                .contains("retry_count")
                .contains("next_retry_time")
                .contains("error_message");
    }

    @Test
    void cancelNotificationShouldOnlyCancelInFlightStates() {
        NotificationRecordServiceImpl service = spy(new NotificationRecordServiceImpl());
        doReturn(false).when(service).update(org.mockito.ArgumentMatchers.<Wrapper<NotificationRecord>>any());

        service.cancelNotification(21L);

        org.mockito.ArgumentCaptor<Wrapper<NotificationRecord>> wrapperCaptor =
                org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(service).update(wrapperCaptor.capture());
        LambdaUpdateWrapper<NotificationRecord> updateWrapper =
                (LambdaUpdateWrapper<NotificationRecord>) wrapperCaptor.getValue();
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("status IN");
        assertThat(updateWrapper.getParamNameValuePairs().values())
                .contains(
                        NotificationStatus.PENDING,
                        NotificationStatus.SENDING,
                        NotificationStatus.RETRY
                );
        assertThat(updateWrapper.getSqlSet())
                .contains("status")
                .contains("next_retry_time");
    }
}
