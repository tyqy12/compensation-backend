package com.yiyundao.compensation.service;

import com.yiyundao.compensation.enums.NotificationChannel;
import com.yiyundao.compensation.enums.NotificationStatus;
import com.yiyundao.compensation.enums.NotificationType;
import com.yiyundao.compensation.interfaces.adapter.NotificationAdapter;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import com.yiyundao.compensation.modules.notification.service.NotificationRecordService;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationRouterServiceTest {

    @Test
    void sendNotificationAsyncShouldSkipWhenPendingClaimLost() throws Exception {
        NotificationRecordService notificationRecordService = mock(NotificationRecordService.class);
        NotificationAdapter adapter = mock(NotificationAdapter.class);
        NotificationRouterService service = new NotificationRouterService(
                notificationRecordService,
                mock(SysUserService.class),
                mock(ExternalIdentityService.class),
                Map.of(NotificationChannel.SYSTEM, adapter)
        );
        NotificationRecord record = new NotificationRecord();
        record.setId(5001L);
        record.setStatus(NotificationStatus.PENDING);
        record.setChannel(NotificationChannel.SYSTEM);
        when(notificationRecordService.update(any())).thenReturn(false);

        Method method = NotificationRouterService.class.getDeclaredMethod("sendNotificationAsync", NotificationRecord.class);
        method.setAccessible(true);
        method.invoke(service, record);

        verify(adapter, never()).sendNotification(any());
        verify(notificationRecordService, never()).updateById(record);
    }

    @Test
    void sendNotificationAsyncShouldScheduleRetryWhenAdapterMissing() throws Exception {
        NotificationRecordService notificationRecordService = mock(NotificationRecordService.class);
        NotificationRouterService service = new NotificationRouterService(
                notificationRecordService,
                mock(SysUserService.class),
                mock(ExternalIdentityService.class),
                Map.of(NotificationChannel.SYSTEM, mock(NotificationAdapter.class))
        );
        NotificationRecord record = new NotificationRecord();
        record.setId(5003L);
        record.setStatus(NotificationStatus.PENDING);
        record.setChannel(NotificationChannel.EMAIL);
        record.setRetryCount(0);
        record.setMaxRetry(3);
        when(notificationRecordService.update(any())).thenReturn(true);

        invokeSendNotificationAsync(service, record);

        assertThat(record.getStatus()).isEqualTo(NotificationStatus.RETRY);
        assertThat(record.getRetryCount()).isZero();
        assertThat(record.getErrorMessage()).contains("未找到通知适配器");
        assertThat(record.getNextRetryTime()).isNotNull();
        verify(notificationRecordService, times(2)).update(any());
        verify(notificationRecordService, never()).updateById(record);
    }

    @Test
    void sendNotificationAsyncShouldNotConsumeRetryAttemptOnInitialFailure() throws Exception {
        NotificationRecordService notificationRecordService = mock(NotificationRecordService.class);
        NotificationAdapter systemAdapter = mock(NotificationAdapter.class);
        when(notificationRecordService.update(any())).thenReturn(true);
        when(systemAdapter.sendNotification(any(NotificationRecord.class)))
                .thenReturn(NotificationAdapter.NotificationSendResult.failure("provider failed"));
        NotificationRouterService service = new NotificationRouterService(
                notificationRecordService,
                mock(SysUserService.class),
                mock(ExternalIdentityService.class),
                Map.of(NotificationChannel.SYSTEM, systemAdapter)
        );
        NotificationRecord record = new NotificationRecord();
        record.setId(5005L);
        record.setStatus(NotificationStatus.PENDING);
        record.setChannel(NotificationChannel.SYSTEM);
        record.setRecipientId("8001");
        record.setRetryCount(0);
        record.setMaxRetry(1);

        invokeSendNotificationAsync(service, record);

        assertThat(record.getStatus()).isEqualTo(NotificationStatus.RETRY);
        assertThat(record.getRetryCount()).isZero();
        assertThat(record.getNextRetryTime()).isNotNull();
        verify(notificationRecordService, times(2)).update(any());
        verify(notificationRecordService, never()).save(any(NotificationRecord.class));
    }

    @Test
    void sendNotificationAsyncShouldNotTriggerFallbackWhenFinalUpdateLost() throws Exception {
        NotificationRecordService notificationRecordService = mock(NotificationRecordService.class);
        NotificationAdapter systemAdapter = mock(NotificationAdapter.class);
        when(notificationRecordService.update(any())).thenReturn(true, false);
        when(systemAdapter.sendNotification(any(NotificationRecord.class)))
                .thenReturn(NotificationAdapter.NotificationSendResult.failure("provider failed"));
        NotificationRouterService service = new NotificationRouterService(
                notificationRecordService,
                mock(SysUserService.class),
                mock(ExternalIdentityService.class),
                Map.of(NotificationChannel.SYSTEM, systemAdapter)
        );
        NotificationRecord record = new NotificationRecord();
        record.setId(5004L);
        record.setStatus(NotificationStatus.PENDING);
        record.setChannel(NotificationChannel.SYSTEM);
        record.setRecipientId("8001");
        record.setRetryCount(0);
        record.setMaxRetry(0);
        record.setFallbackChannels("[\"system\"]");

        invokeSendNotificationAsync(service, record);

        assertThat(record.getStatus()).isEqualTo(NotificationStatus.FAILED);
        verify(notificationRecordService, never()).save(any(NotificationRecord.class));
        verify(notificationRecordService, never()).updateById(record);
        verify(notificationRecordService, times(2)).update(any());
    }

    @Test
    void sendNotificationToUserShouldSkipUnavailableEmailChannelAndUseSystemFallback() {
        NotificationRecordService notificationRecordService = mock(NotificationRecordService.class);
        SysUserService sysUserService = mock(SysUserService.class);
        ExternalIdentityService externalIdentityService = mock(ExternalIdentityService.class);
        NotificationAdapter systemAdapter = mock(NotificationAdapter.class);
        when(systemAdapter.sendNotification(any(NotificationRecord.class)))
                .thenReturn(NotificationAdapter.NotificationSendResult.success("SYSTEM", "ok"));
        when(notificationRecordService.update(any())).thenReturn(true);
        doAnswer(invocation -> {
            NotificationRecord record = invocation.getArgument(0);
            record.setId(7001L);
            return true;
        }).when(notificationRecordService).save(any(NotificationRecord.class));
        SysUser user = new SysUser();
        user.setId(8001L);
        user.setUsername("zhangsan");
        user.setEmail("zhangsan@example.com");
        when(sysUserService.getById(8001L)).thenReturn(user);

        NotificationRouterService service = new NotificationRouterService(
                notificationRecordService,
                sysUserService,
                externalIdentityService,
                Map.of(NotificationChannel.SYSTEM, systemAdapter)
        );

        service.sendNotificationToUser(8001L, NotificationType.SYSTEM_ALERT, "标题", "内容", "SYSTEM", "BIZ-8001");

        ArgumentCaptor<NotificationRecord> recordCaptor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(notificationRecordService).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getChannel()).isEqualTo(NotificationChannel.SYSTEM);
        assertThat(recordCaptor.getValue().getFallbackChannels()).isEqualTo("[]");
        verify(systemAdapter).sendNotification(recordCaptor.getValue());
    }

    @Test
    void sendNotificationToUserShouldNormalizePrimaryIdentityProviderAlias() {
        NotificationRecordService notificationRecordService = mock(NotificationRecordService.class);
        SysUserService sysUserService = mock(SysUserService.class);
        ExternalIdentityService externalIdentityService = mock(ExternalIdentityService.class);
        NotificationAdapter wechatAdapter = mock(NotificationAdapter.class);
        when(wechatAdapter.sendNotification(any(NotificationRecord.class)))
                .thenReturn(NotificationAdapter.NotificationSendResult.success("WECHAT", "ok"));
        when(notificationRecordService.update(any())).thenReturn(true);
        doAnswer(invocation -> {
            NotificationRecord record = invocation.getArgument(0);
            record.setId(7003L);
            return true;
        }).when(notificationRecordService).save(any(NotificationRecord.class));
        SysUser user = new SysUser();
        user.setId(8002L);
        user.setUsername("lisi");
        when(sysUserService.getById(8002L)).thenReturn(user);
        ExternalIdentity primaryIdentity = new ExternalIdentity();
        primaryIdentity.setProvider("wecom");
        when(externalIdentityService.findPrimaryByUserId(8002L)).thenReturn(primaryIdentity);
        ExternalIdentity wechatIdentity = new ExternalIdentity();
        wechatIdentity.setSubjectId("wx-user-8002");
        when(externalIdentityService.findByUserIdAndProvider(8002L, "wechat")).thenReturn(wechatIdentity);

        NotificationRouterService service = new NotificationRouterService(
                notificationRecordService,
                sysUserService,
                externalIdentityService,
                Map.of(NotificationChannel.WECHAT, wechatAdapter)
        );

        service.sendNotificationToUser(8002L, NotificationType.SYSTEM_ALERT, "标题", "内容", "SYSTEM", "BIZ-8002");

        ArgumentCaptor<NotificationRecord> recordCaptor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(notificationRecordService).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getChannel()).isEqualTo(NotificationChannel.WECHAT);
        assertThat(recordCaptor.getValue().getRecipientId()).isEqualTo("wx-user-8002");
        verify(wechatAdapter).sendNotification(recordCaptor.getValue());
    }

    @Test
    void sendNotificationToPlatformUserShouldSkipWhenAdapterMissing() {
        NotificationRecordService notificationRecordService = mock(NotificationRecordService.class);
        NotificationRouterService service = new NotificationRouterService(
                notificationRecordService,
                mock(SysUserService.class),
                mock(ExternalIdentityService.class),
                Map.of(NotificationChannel.SYSTEM, mock(NotificationAdapter.class))
        );

        service.sendNotificationToPlatformUser(
                "feishu",
                "ou-1001",
                NotificationType.SYSTEM_ALERT,
                "标题",
                "内容",
                "SYSTEM",
                "BIZ-9001"
        );

        verify(notificationRecordService, never()).save(any(NotificationRecord.class));
        verify(notificationRecordService, never()).update(any());
    }

    @Test
    void sendNotificationToPlatformUserShouldNormalizeWeComProviderAlias() {
        NotificationRecordService notificationRecordService = mock(NotificationRecordService.class);
        NotificationAdapter wechatAdapter = mock(NotificationAdapter.class);
        when(notificationRecordService.update(any())).thenReturn(true);
        when(wechatAdapter.sendNotification(any(NotificationRecord.class)))
                .thenReturn(NotificationAdapter.NotificationSendResult.success("WECHAT", "ok"));
        doAnswer(invocation -> {
            NotificationRecord record = invocation.getArgument(0);
            record.setId(7002L);
            return true;
        }).when(notificationRecordService).save(any(NotificationRecord.class));
        NotificationRouterService service = new NotificationRouterService(
                notificationRecordService,
                mock(SysUserService.class),
                mock(ExternalIdentityService.class),
                Map.of(NotificationChannel.WECHAT, wechatAdapter)
        );

        service.sendNotificationToPlatformUser(
                "wecom",
                "wx-user-7002",
                NotificationType.SYSTEM_ALERT,
                "标题",
                "内容",
                "SYSTEM",
                "BIZ-7002"
        );

        ArgumentCaptor<NotificationRecord> recordCaptor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(notificationRecordService).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getChannel()).isEqualTo(NotificationChannel.WECHAT);
        assertThat(recordCaptor.getValue().getRecipientId()).isEqualTo("wx-user-7002");
        verify(wechatAdapter).sendNotification(recordCaptor.getValue());
    }

    @Test
    void createFallbackNotificationShouldResolveSmsRecipientFromOriginalPlatformUser() throws Exception {
        NotificationRecordService notificationRecordService = mock(NotificationRecordService.class);
        SysUserService sysUserService = mock(SysUserService.class);
        ExternalIdentityService externalIdentityService = mock(ExternalIdentityService.class);
        NotificationAdapter smsAdapter = mock(NotificationAdapter.class);
        when(notificationRecordService.update(any())).thenReturn(true);
        doAnswer(invocation -> {
            NotificationRecord record = invocation.getArgument(0);
            record.setId(6001L);
            return true;
        }).when(notificationRecordService).save(any(NotificationRecord.class));
        when(smsAdapter.sendNotification(any(NotificationRecord.class)))
                .thenReturn(NotificationAdapter.NotificationSendResult.success("200", "ok"));

        NotificationRouterService service = new NotificationRouterService(
                notificationRecordService,
                sysUserService,
                externalIdentityService,
                Map.of(NotificationChannel.SMS, smsAdapter)
        );
        ExternalIdentity identity = new ExternalIdentity();
        identity.setUserId(7001L);
        when(externalIdentityService.findActiveIdentity(
                eq("wechat"),
                eq(ExternalIdentityService.DEFAULT_TENANT_KEY),
                eq(ExternalIdentityService.DEFAULT_SUBJECT_TYPE),
                eq("wx-openid-7001")
        )).thenReturn(identity);
        SysUser user = new SysUser();
        user.setId(7001L);
        user.setPhone("13800138000");
        when(sysUserService.getById(7001L)).thenReturn(user);

        NotificationRecord original = platformRecord();
        service.createAndSendFallbackNotification(original, NotificationChannel.SMS);

        ArgumentCaptor<NotificationRecord> recordCaptor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(notificationRecordService).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getChannel()).isEqualTo(NotificationChannel.SMS);
        assertThat(recordCaptor.getValue().getRecipientId()).isEqualTo("13800138000");
        verify(smsAdapter).sendNotification(recordCaptor.getValue());
    }

    @Test
    void createFallbackNotificationShouldSkipCrossChannelFallbackWhenUserCannotBeResolved() throws Exception {
        NotificationRecordService notificationRecordService = mock(NotificationRecordService.class);
        NotificationRouterService service = new NotificationRouterService(
                notificationRecordService,
                mock(SysUserService.class),
                mock(ExternalIdentityService.class),
                Map.of(NotificationChannel.SMS, mock(NotificationAdapter.class))
        );

        service.createAndSendFallbackNotification(platformRecord(), NotificationChannel.SMS);

        verify(notificationRecordService, never()).save(any(NotificationRecord.class));
        verify(notificationRecordService, never()).update(any());
    }

    private void invokeSendNotificationAsync(NotificationRouterService service, NotificationRecord record) throws Exception {
        Method method = NotificationRouterService.class.getDeclaredMethod("sendNotificationAsync", NotificationRecord.class);
        method.setAccessible(true);
        method.invoke(service, record);
    }

    private NotificationRecord platformRecord() {
        NotificationRecord record = new NotificationRecord();
        record.setId(5002L);
        record.setNotificationType(NotificationType.SYSTEM_ALERT);
        record.setChannel(NotificationChannel.WECHAT);
        record.setRecipientId("wx-openid-7001");
        record.setRecipientName("张三");
        record.setTitle("通知标题");
        record.setContent("通知内容");
        record.setBusinessType("SYSTEM");
        record.setBusinessKey("BIZ-5002");
        record.setPriority(10);
        return record;
    }
}
