package com.yiyundao.compensation.service;

import com.yiyundao.compensation.enums.NotificationType;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class NotificationServiceTest {

    private final NotificationRouterService notificationRouterService = mock(NotificationRouterService.class);
    private final SysConfigService sysConfigService = mock(SysConfigService.class);
    private final SysUserService sysUserService = mock(SysUserService.class);
    private final NotificationService service = new NotificationService(
            notificationRouterService, sysConfigService, sysUserService);

    @Test
    void sendPaymentSuccessNotificationShouldNotLogAmount(CapturedOutput output) {
        PaymentRecord record = new PaymentRecord();
        record.setId(3001L);
        record.setUserId(9001L);
        record.setBatchNo("BATCH-001");
        record.setAmount(new BigDecimal("12345.67"));
        record.setPaymentDesc("6月工资");

        service.sendPaymentSuccessNotification(record);

        assertThat(output).contains("recordId=3001");
        assertThat(output).contains("userId=9001");
        assertThat(output).contains("batchNo=BATCH-001");
        assertThat(output).doesNotContain("12345.67");
        verify(notificationRouterService).sendNotificationToUser(
                eq(9001L), eq(NotificationType.PAYMENT_SUCCESS), eq("💰 支付成功通知"),
                org.mockito.ArgumentMatchers.contains("12345.67"), eq("PAYMENT"), eq("BATCH-001"));
    }

    @Test
    void sendFallbackNotificationShouldNotLogMessageBody(CapturedOutput output) {
        String message = "工资发放失败，请联系财务，金额 8888.66";

        service.sendFallbackNotification("wecom", "u-1001", message);

        assertThat(output).contains("provider=wecom");
        assertThat(output).contains("subjectId=u-1001");
        assertThat(output).contains("messageLength=");
        assertThat(output).doesNotContain("8888.66");
        assertThat(output).doesNotContain("工资发放失败");
        verify(notificationRouterService).sendNotificationToPlatformUser(
                eq("wecom"), eq("u-1001"), eq(NotificationType.SYSTEM_ALERT), eq("通知发送失败"),
                eq(message), eq("FALLBACK"), eq("wecom_u-1001"));
    }

    @Test
    void sendPaymentSuccessNotificationShouldResolveUserByEmployeeId(CapturedOutput output) {
        PaymentRecord record = new PaymentRecord();
        record.setId(3003L);
        record.setEmployeeId(7001L);
        record.setBatchNo("BATCH-EMPLOYEE");
        record.setAmount(new BigDecimal("4567.89"));
        record.setPaymentDesc("6月工资");

        SysUser user = new SysUser();
        user.setId(9002L);
        when(sysUserService.findByEmployeeId(7001L)).thenReturn(user);

        service.sendPaymentSuccessNotification(record);

        assertThat(record.getUserId()).isEqualTo(9002L);
        assertThat(output).contains("recordId=3003");
        assertThat(output).contains("userId=9002");
        assertThat(output).contains("employeeId=7001");
        assertThat(output).doesNotContain("4567.89");
        verify(notificationRouterService).sendNotificationToUser(
                eq(9002L), eq(NotificationType.PAYMENT_SUCCESS), eq("💰 支付成功通知"),
                org.mockito.ArgumentMatchers.contains("4567.89"), eq("PAYMENT"), eq("BATCH-EMPLOYEE"));
    }

    @Test
    void sendPaymentSuccessNotificationShouldFormatMissingFieldsSafely() {
        PaymentRecord record = new PaymentRecord();
        record.setId(3005L);
        record.setUserId(9004L);
        record.setBatchNo("BATCH-MISSING-AMOUNT");

        service.sendPaymentSuccessNotification(record);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationRouterService).sendNotificationToUser(
                eq(9004L), eq(NotificationType.PAYMENT_SUCCESS), eq("💰 支付成功通知"),
                contentCaptor.capture(), eq("PAYMENT"), eq("BATCH-MISSING-AMOUNT"));
        assertThat(contentCaptor.getValue()).contains("您的 款项 已成功到账");
        assertThat(contentCaptor.getValue()).contains("金额：未知");
        assertThat(contentCaptor.getValue()).contains("支付时间：未知");
        assertThat(contentCaptor.getValue()).doesNotContain("¥nu");
    }

    @Test
    void sendPaymentFailedNotificationShouldResolveUserByEmployeeId() {
        PaymentRecord record = new PaymentRecord();
        record.setId(3004L);
        record.setEmployeeId(7002L);
        record.setBatchNo("BATCH-FAILED");
        record.setAmount(new BigDecimal("88.00"));
        record.setPaymentDesc("报销款");
        record.setErrorMsg("账户异常");

        SysUser user = new SysUser();
        user.setId(9003L);
        when(sysUserService.findByEmployeeId(7002L)).thenReturn(user);

        service.sendPaymentFailedNotification(record);

        assertThat(record.getUserId()).isEqualTo(9003L);
        verify(notificationRouterService).sendNotificationToUser(
                eq(9003L), eq(NotificationType.PAYMENT_FAILED), eq("❌ 支付失败通知"),
                org.mockito.ArgumentMatchers.contains("账户异常"), eq("PAYMENT"), eq("BATCH-FAILED"));
    }

    @Test
    void sendPaymentSuccessNotificationShouldSkipWhenUserMissing(CapturedOutput output) {
        PaymentRecord record = new PaymentRecord();
        record.setId(3002L);
        record.setBatchNo("BATCH-NULL-USER");
        record.setAmount(new BigDecimal("99.99"));

        service.sendPaymentSuccessNotification(record);

        assertThat(output).contains("缺少接收用户");
        assertThat(output).contains("recordId=3002");
        verify(notificationRouterService, never()).sendNotificationToUser(
                org.mockito.ArgumentMatchers.anyLong(),
                eq(NotificationType.PAYMENT_SUCCESS),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                eq("PAYMENT"),
                eq("BATCH-NULL-USER"));
    }

    @Test
    void sendBatchCompleteNotificationShouldNotLogTotalAmount(CapturedOutput output) {
        PaymentBatch batch = new PaymentBatch();
        batch.setBatchNo("PB-100");
        batch.setProcessorId(6001L);
        batch.setTotalCount(12);
        batch.setSuccessCount(10);
        batch.setFailedCount(2);
        batch.setTotalAmount(new BigDecimal("56000.88"));
        batch.setBatchName("六月发薪");

        service.sendBatchCompleteNotification(batch);

        assertThat(output).contains("batchNo=PB-100");
        assertThat(output).contains("processorId=6001");
        assertThat(output).doesNotContain("56000.88");
    }

    @Test
    void sendBatchCompleteNotificationShouldFormatMissingMetricsSafely() {
        PaymentBatch batch = new PaymentBatch();
        batch.setBatchNo("PB-MISSING");
        batch.setProcessorId(6002L);

        service.sendBatchCompleteNotification(batch);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationRouterService).sendNotificationToUser(
                eq(6002L), eq(NotificationType.BATCH_COMPLETE), eq("📊 批量支付完成通知"),
                contentCaptor.capture(), eq("PAYMENT_BATCH"), eq("PB-MISSING"));
        assertThat(contentCaptor.getValue()).contains("批次名称：未命名批次");
        assertThat(contentCaptor.getValue()).contains("总笔数：未知");
        assertThat(contentCaptor.getValue()).contains("总金额：未知");
        assertThat(contentCaptor.getValue()).doesNotContain("null 笔");
        assertThat(contentCaptor.getValue()).doesNotContain("¥nu");
    }

    @Test
    void sendSystemAlertShouldNotLogTitleOrMessageOnFailure(CapturedOutput output) {
        when(sysConfigService.getLong("system.admin_user_id", 1L)).thenReturn(1L);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(notificationRouterService)
                .sendNotificationToUser(eq(1L), eq(NotificationType.SYSTEM_ALERT), eq("🚨 告警标题"),
                        eq("工资异常 9988.12"), eq("SYSTEM"), eq("biz-100"));

        service.sendSystemAlert("告警标题", "工资异常 9988.12", "biz-100");

        assertThat(output).contains("businessKey=biz-100");
        assertThat(output).contains("titleLength=");
        assertThat(output).contains("messageLength=");
        assertThat(output).doesNotContain("告警标题");
        assertThat(output).doesNotContain("9988.12");
    }
}
