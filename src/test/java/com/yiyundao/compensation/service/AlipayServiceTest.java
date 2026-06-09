package com.yiyundao.compensation.service;

import com.alipay.api.internal.util.AlipaySignature;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AlipayServiceTest {

    @Mock
    private PaymentRecordService paymentRecordService;

    @Mock
    private PaymentBatchService paymentBatchService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private NotificationService notificationService;

    @Mock
    private IntegrationConfigService integrationConfigService;

    @Mock
    private PayrollBatchMapper payrollBatchMapper;

    @Test
    void queryTransferStatusShouldRejectPlaceholderPrivateKeyBeforeSdkSigning() {
        AlipayService service = new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper);
        AlipayConfigDto config = new AlipayConfigDto();
        config.setAppId("test-app-id");
        config.setPrivateKey("test-private-key");
        config.setPublicKey("alipay-public-key");
        when(integrationConfigService.getAlipayConfig()).thenReturn(config);

        assertThatThrownBy(() -> service.queryTransferStatus("COMP_1772756263316_928CC3C9"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PKCS8");
    }

    @Test
    void verifyNotificationShouldUseCallbackCharsetAndSignTypeWithoutMutatingParams() throws Exception {
        AlipayService service = new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper);
        java.security.KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String privateKey = java.util.Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = java.util.Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        AlipayConfigDto config = new AlipayConfigDto();
        config.setPublicKey(publicKey);
        config.setCharset("GBK");
        config.setSignType("RSA");
        when(integrationConfigService.getAlipayConfig()).thenReturn(config);

        Map<String, String> params = new TreeMap<>();
        params.put("out_biz_no", "OUT-VERIFY-1");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("charset", "UTF-8");
        params.put("sign_type", "RSA2");
        params.put("sign", AlipaySignature.rsaSign(alipayV1SignContent(params), privateKey, "UTF-8", "RSA2"));

        boolean verified = service.verifyNotification(params);

        assertThat(verified).isTrue();
        assertThat(params).containsKeys("sign", "sign_type");
    }

    @Test
    void verifyNotificationShouldRejectCallbackWithDifferentAppId() throws Exception {
        AlipayService service = new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper);
        java.security.KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String privateKey = java.util.Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = java.util.Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        AlipayConfigDto config = new AlipayConfigDto();
        config.setAppId("APP_EXPECTED");
        config.setPublicKey(publicKey);
        when(integrationConfigService.getAlipayConfig()).thenReturn(config);

        Map<String, String> params = new TreeMap<>();
        params.put("app_id", "APP_OTHER");
        params.put("out_biz_no", "OUT-VERIFY-APP-1");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("sign", AlipaySignature.rsaSign(alipayV1SignContent(params), privateKey, "UTF-8", "RSA2"));

        boolean verified = service.verifyNotification(params);

        assertThat(verified).isFalse();
    }

    @Test
    void verifyNotificationShouldRejectCallbackAppIdWhenConfigAppIdMissing() throws Exception {
        AlipayService service = new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper);
        java.security.KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String privateKey = java.util.Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = java.util.Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        AlipayConfigDto config = new AlipayConfigDto();
        config.setPublicKey(publicKey);
        when(integrationConfigService.getAlipayConfig()).thenReturn(config);

        Map<String, String> params = new TreeMap<>();
        params.put("app_id", "APP_PRESENT");
        params.put("out_biz_no", "OUT-VERIFY-APP-2");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("sign", AlipaySignature.rsaSign(alipayV1SignContent(params), privateKey, "UTF-8", "RSA2"));

        boolean verified = service.verifyNotification(params);

        assertThat(verified).isFalse();
    }

    private String alipayV1SignContent(Map<String, String> params) {
        Map<String, String> signParams = new TreeMap<>(params);
        signParams.remove("sign");
        signParams.remove("sign_type");
        return AlipaySignature.getSignContent(signParams);
    }


    @Test
    void syncPayrollBatchStatusShouldMarkPartialSuccessAsPayFailed() {
        AlipayService service = new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper);
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-2001");
        paymentBatch.setStatus(BatchStatus.COMPLETED);
        paymentBatch.setPaymentStatus(PaymentBatchProcessStatus.PARTIAL_SUCCESS);
        paymentBatch.setSuccessCount(8);
        paymentBatch.setFailedCount(2);

        service.syncPayrollBatchStatus(paymentBatch);

        ArgumentCaptor<UpdateWrapper<PayrollBatch>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollBatchMapper).update(isNull(), wrapperCaptor.capture());
        UpdateWrapper<PayrollBatch> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSet()).contains("status");
        assertThat(wrapper.getSqlSegment()).contains("status IN");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(PayrollBatchStatus.PAY_FAILED.getCode(),
                        PayrollBatchStatus.PAY_PROCESSING.getCode());
    }

    @Test
    void syncPayrollBatchStatusShouldMarkAllSuccessAsPaid() {
        AlipayService service = new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper);
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-2002");
        paymentBatch.setStatus(BatchStatus.COMPLETED);
        paymentBatch.setPaymentStatus(PaymentBatchProcessStatus.SUCCESS);
        paymentBatch.setSuccessCount(10);
        paymentBatch.setFailedCount(0);

        service.syncPayrollBatchStatus(paymentBatch);

        ArgumentCaptor<UpdateWrapper<PayrollBatch>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollBatchMapper).update(isNull(), wrapperCaptor.capture());
        UpdateWrapper<PayrollBatch> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSet()).contains("status");
        assertThat(wrapper.getSqlSegment()).contains("status IN");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(PayrollBatchStatus.PAID.getCode(),
                        PayrollBatchStatus.PAY_FAILED.getCode(),
                        PayrollBatchStatus.PAY_PROCESSING.getCode());
    }

    @Test
    void batchTransferShouldPersistFailedBatchWhenAllRecordsFail() throws Exception {
        AlipayService service = spy(new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper));
        PaymentBatch batch = new PaymentBatch();
        batch.setId(301L);
        batch.setBatchNo("PB-FAIL-1");
        batch.setStatus(BatchStatus.SUBMITTED);
        PaymentRecord record = new PaymentRecord();
        record.setId(401L);

        when(paymentBatchService.getByBatchNo("PB-FAIL-1")).thenReturn(batch);
        when(paymentBatchService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(paymentRecordService.getByBatchNo("PB-FAIL-1", com.yiyundao.compensation.enums.PaymentStatus.PENDING))
                .thenReturn(java.util.List.of(record));
        doThrow(new IllegalStateException("provider down")).when(service).singleTransfer(401L);

        assertThatCode(() -> service.batchTransfer("PB-FAIL-1")).doesNotThrowAnyException();

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(batch.getPaymentStatus()).isEqualTo(PaymentBatchProcessStatus.FAILED);
        verify(paymentBatchService).update(any(UpdateWrapper.class));
        ArgumentCaptor<PaymentBatch> batchCaptor = ArgumentCaptor.forClass(PaymentBatch.class);
        verify(paymentBatchService).updateTerminalState(batchCaptor.capture());
        List<PaymentBatch> persistedBatches = batchCaptor.getAllValues();
        PaymentBatch terminalBatch = persistedBatches.get(persistedBatches.size() - 1);
        assertThat(terminalBatch.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(terminalBatch.getPaymentStatus()).isEqualTo(PaymentBatchProcessStatus.FAILED);
    }

    @Test
    void batchTransferShouldSkipWhenBatchClaimFails() throws Exception {
        AlipayService service = spy(new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper));
        PaymentBatch batch = new PaymentBatch();
        batch.setId(302L);
        batch.setBatchNo("PB-CLAIM-LOST");
        batch.setStatus(BatchStatus.SUBMITTED);

        when(paymentBatchService.getByBatchNo("PB-CLAIM-LOST")).thenReturn(batch);
        when(paymentBatchService.update(any(UpdateWrapper.class))).thenReturn(false);

        service.batchTransfer("PB-CLAIM-LOST");

        verify(paymentRecordService, never()).getByBatchNo("PB-CLAIM-LOST",
                com.yiyundao.compensation.enums.PaymentStatus.PENDING);
        verify(service, never()).singleTransfer(any());
        verify(paymentBatchService, never()).updateTerminalState(any(PaymentBatch.class));
        verify(notificationService, never()).sendBatchCompleteNotification(any(PaymentBatch.class));
    }

    @Test
    void handleNotificationShouldRefreshBatchAndPayrollStatusAfterSuccess() {
        AlipayService service = new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper);
        PaymentRecord record = new PaymentRecord();
        record.setId(501L);
        record.setBatchNo("PB-CALLBACK-1");
        PaymentRecord successRecord = new PaymentRecord();
        successRecord.setId(501L);
        successRecord.setBatchNo("PB-CALLBACK-1");
        successRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.SUCCESS);
        PaymentBatch batch = new PaymentBatch();
        batch.setId(601L);
        batch.setBatchNo("PB-CALLBACK-1");
        batch.setStatus(BatchStatus.PROCESSING);

        when(paymentRecordService.getByProviderOrderNo("alipay", "OUT-501")).thenReturn(record);
        when(paymentRecordService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(paymentBatchService.getByBatchNo("PB-CALLBACK-1")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("PB-CALLBACK-1", null)).thenReturn(List.of(successRecord));

        service.handleNotification("OUT-501", "TRADE-501", "TRADE_SUCCESS");

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(batch.getPaymentStatus()).isEqualTo(PaymentBatchProcessStatus.SUCCESS);
        verify(paymentBatchService).updateTerminalState(batch);
        verify(notificationService).sendBatchCompleteNotification(batch);
        verify(notificationService).sendPaymentSuccessNotification(any(PaymentRecord.class));
    }

    @Test
    void handleNotificationShouldKeepBatchProcessingWhenOtherRecordsPending() {
        AlipayService service = new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper);
        PaymentRecord record = new PaymentRecord();
        record.setId(701L);
        record.setBatchNo("PB-CALLBACK-2");
        PaymentRecord successRecord = new PaymentRecord();
        successRecord.setId(701L);
        successRecord.setBatchNo("PB-CALLBACK-2");
        successRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.SUCCESS);
        PaymentRecord pendingRecord = new PaymentRecord();
        pendingRecord.setId(702L);
        pendingRecord.setBatchNo("PB-CALLBACK-2");
        pendingRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PENDING);
        PaymentBatch batch = new PaymentBatch();
        batch.setId(801L);
        batch.setBatchNo("PB-CALLBACK-2");
        batch.setStatus(BatchStatus.PROCESSING);

        when(paymentRecordService.getByProviderOrderNo("alipay", "OUT-701")).thenReturn(record);
        when(paymentRecordService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(paymentBatchService.getByBatchNo("PB-CALLBACK-2")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("PB-CALLBACK-2", null)).thenReturn(List.of(successRecord, pendingRecord));

        service.handleNotification("OUT-701", "TRADE-701", "TRADE_SUCCESS");

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PROCESSING);
        assertThat(batch.getPaymentStatus()).isEqualTo(PaymentBatchProcessStatus.PROCESSING);
        verify(paymentBatchService).updateTerminalState(batch);
        verify(payrollBatchMapper, never()).update(isNull(), any(UpdateWrapper.class));
        verify(notificationService, never()).sendBatchCompleteNotification(any(PaymentBatch.class));
    }

    @Test
    void handleNotificationShouldTreatTradeFinishedAsSuccess() {
        AlipayService service = new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper);
        PaymentRecord record = new PaymentRecord();
        record.setId(901L);
        record.setBatchNo("PB-CALLBACK-3");
        PaymentRecord finishedRecord = new PaymentRecord();
        finishedRecord.setId(901L);
        finishedRecord.setBatchNo("PB-CALLBACK-3");
        finishedRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.SUCCESS);
        PaymentBatch batch = new PaymentBatch();
        batch.setId(902L);
        batch.setBatchNo("PB-CALLBACK-3");
        batch.setStatus(BatchStatus.PROCESSING);

        when(paymentRecordService.getByProviderOrderNo("alipay", "OUT-901")).thenReturn(record);
        when(paymentRecordService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(paymentBatchService.getByBatchNo("PB-CALLBACK-3")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("PB-CALLBACK-3", null)).thenReturn(List.of(finishedRecord));

        service.handleNotification("OUT-901", "TRADE-901", "TRADE_FINISHED");

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(batch.getPaymentStatus()).isEqualTo(PaymentBatchProcessStatus.SUCCESS);
        verify(paymentBatchService).updateTerminalState(batch);
        verify(notificationService).sendBatchCompleteNotification(batch);
        verify(notificationService).sendPaymentSuccessNotification(any(PaymentRecord.class));
    }

    @Test
    void handleNotificationShouldRecoverCancelledBatchWhenLateSuccessArrives() {
        AlipayService service = new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper);
        PaymentRecord cancelledRecord = new PaymentRecord();
        cancelledRecord.setId(1001L);
        cancelledRecord.setBatchNo("PB-CALLBACK-4");
        cancelledRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.CANCELLED);
        PaymentRecord correctedRecord = new PaymentRecord();
        correctedRecord.setId(1001L);
        correctedRecord.setBatchNo("PB-CALLBACK-4");
        correctedRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.SUCCESS);
        PaymentBatch batch = new PaymentBatch();
        batch.setId(1002L);
        batch.setBatchNo("PB-CALLBACK-4");
        batch.setStatus(BatchStatus.FAILED);
        batch.setPaymentStatus(PaymentBatchProcessStatus.FAILED);
        batch.setSuccessCount(0);
        batch.setFailedCount(1);

        when(paymentRecordService.getByProviderOrderNo("alipay", "OUT-1001")).thenReturn(cancelledRecord);
        when(paymentRecordService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(paymentBatchService.getByBatchNo("PB-CALLBACK-4")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("PB-CALLBACK-4", null)).thenReturn(List.of(correctedRecord));

        service.handleNotification("OUT-1001", "TRADE-1001", "TRADE_SUCCESS");

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(batch.getPaymentStatus()).isEqualTo(PaymentBatchProcessStatus.SUCCESS);
        assertThat(batch.getSuccessCount()).isEqualTo(1);
        assertThat(batch.getFailedCount()).isZero();
        verify(paymentBatchService).updateTerminalState(batch);
        verify(notificationService).sendBatchCompleteNotification(batch);
        verify(notificationService).sendPaymentSuccessNotification(any(PaymentRecord.class));
    }

    @Test
    void handleNotificationShouldNotReopenTerminalFailedBatchWhenRecordsRemainProcessing() {
        AlipayService service = new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper);
        PaymentRecord record = new PaymentRecord();
        record.setId(1051L);
        record.setBatchNo("PB-CALLBACK-STILL-PROCESSING");
        record.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PROCESSING);
        PaymentRecord successRecord = new PaymentRecord();
        successRecord.setId(1051L);
        successRecord.setBatchNo("PB-CALLBACK-STILL-PROCESSING");
        successRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.SUCCESS);
        PaymentRecord pendingRecord = new PaymentRecord();
        pendingRecord.setId(1052L);
        pendingRecord.setBatchNo("PB-CALLBACK-STILL-PROCESSING");
        pendingRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PENDING);
        PaymentBatch batch = new PaymentBatch();
        batch.setId(1053L);
        batch.setBatchNo("PB-CALLBACK-STILL-PROCESSING");
        batch.setStatus(BatchStatus.FAILED);
        batch.setPaymentStatus(PaymentBatchProcessStatus.FAILED);
        batch.setSuccessCount(0);
        batch.setFailedCount(2);

        when(paymentRecordService.getByProviderOrderNo("alipay", "OUT-1051")).thenReturn(record);
        when(paymentRecordService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(paymentBatchService.getByBatchNo("PB-CALLBACK-STILL-PROCESSING")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("PB-CALLBACK-STILL-PROCESSING", null))
                .thenReturn(List.of(successRecord, pendingRecord));

        service.handleNotification("OUT-1051", "TRADE-1051", "TRADE_SUCCESS");

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(batch.getPaymentStatus()).isEqualTo(PaymentBatchProcessStatus.FAILED);
        verify(paymentBatchService, never()).updateTerminalState(any(PaymentBatch.class));
        verify(payrollBatchMapper, never()).update(isNull(), any(UpdateWrapper.class));
        verify(notificationService, never()).sendBatchCompleteNotification(any(PaymentBatch.class));
    }

    @Test
    void handleNotificationShouldSendFailureNotificationWhenTradeClosed() {
        AlipayService service = new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper);
        PaymentRecord record = new PaymentRecord();
        record.setId(1101L);
        record.setBatchNo("PB-CALLBACK-5");
        record.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PROCESSING);
        PaymentRecord failedRecord = new PaymentRecord();
        failedRecord.setId(1101L);
        failedRecord.setBatchNo("PB-CALLBACK-5");
        failedRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.FAILED);
        PaymentBatch batch = new PaymentBatch();
        batch.setId(1102L);
        batch.setBatchNo("PB-CALLBACK-5");
        batch.setStatus(BatchStatus.PROCESSING);

        when(paymentRecordService.getByProviderOrderNo("alipay", "OUT-1101")).thenReturn(record);
        when(paymentRecordService.update(any(UpdateWrapper.class))).thenReturn(true);
        when(paymentBatchService.getByBatchNo("PB-CALLBACK-5")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("PB-CALLBACK-5", null)).thenReturn(List.of(failedRecord));

        service.handleNotification("OUT-1101", "TRADE-1101", "TRADE_CLOSED");

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(batch.getPaymentStatus()).isEqualTo(PaymentBatchProcessStatus.FAILED);
        ArgumentCaptor<UpdateWrapper<PaymentRecord>> recordWrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(paymentRecordService, atLeast(1)).update(recordWrapperCaptor.capture());
        UpdateWrapper<PaymentRecord> recordWrapper = recordWrapperCaptor.getAllValues().stream()
                .filter(wrapper -> wrapper.getSqlSet() != null && wrapper.getSqlSet().contains("error_code"))
                .findFirst()
                .orElseThrow();
        assertThat(recordWrapper.getSqlSet()).contains("error_code", "error_msg");
        assertThat(recordWrapper.getParamNameValuePairs().values())
                .contains("ALIPAY_TRADE_CLOSED", "支付宝回调确认支付失败");
        verify(notificationService).sendPaymentFailedNotification(any(PaymentRecord.class));
    }

    @Test
    void handleNotificationShouldNotSendDuplicateSuccessNotificationWhenClaimFails() {
        AlipayService service = new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper);
        PaymentRecord record = new PaymentRecord();
        record.setId(1201L);
        record.setBatchNo("PB-CALLBACK-DUP-SUCCESS");
        record.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PROCESSING);
        PaymentRecord successRecord = new PaymentRecord();
        successRecord.setId(1201L);
        successRecord.setBatchNo("PB-CALLBACK-DUP-SUCCESS");
        successRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.SUCCESS);
        PaymentBatch batch = new PaymentBatch();
        batch.setId(1202L);
        batch.setBatchNo("PB-CALLBACK-DUP-SUCCESS");
        batch.setStatus(BatchStatus.PROCESSING);

        when(paymentRecordService.getByProviderOrderNo("alipay", "OUT-1201")).thenReturn(record);
        when(paymentRecordService.update(any(UpdateWrapper.class))).thenReturn(true, false);
        when(paymentBatchService.getByBatchNo("PB-CALLBACK-DUP-SUCCESS")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("PB-CALLBACK-DUP-SUCCESS", null)).thenReturn(List.of(successRecord));

        service.handleNotification("OUT-1201", "TRADE-1201", "TRADE_SUCCESS");

        verify(notificationService, never()).sendPaymentSuccessNotification(any(PaymentRecord.class));
    }

    @Test
    void handleNotificationShouldNotSendDuplicateFailedNotificationWhenClaimFails() {
        AlipayService service = new AlipayService(
                paymentRecordService,
                paymentBatchService,
                redisTemplate,
                notificationService,
                integrationConfigService,
                payrollBatchMapper);
        PaymentRecord record = new PaymentRecord();
        record.setId(1301L);
        record.setBatchNo("PB-CALLBACK-DUP-FAILED");
        record.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PROCESSING);
        PaymentRecord failedRecord = new PaymentRecord();
        failedRecord.setId(1301L);
        failedRecord.setBatchNo("PB-CALLBACK-DUP-FAILED");
        failedRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.FAILED);
        PaymentBatch batch = new PaymentBatch();
        batch.setId(1302L);
        batch.setBatchNo("PB-CALLBACK-DUP-FAILED");
        batch.setStatus(BatchStatus.PROCESSING);

        when(paymentRecordService.getByProviderOrderNo("alipay", "OUT-1301")).thenReturn(record);
        when(paymentRecordService.update(any(UpdateWrapper.class))).thenReturn(true, false);
        when(paymentBatchService.getByBatchNo("PB-CALLBACK-DUP-FAILED")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("PB-CALLBACK-DUP-FAILED", null)).thenReturn(List.of(failedRecord));

        service.handleNotification("OUT-1301", "TRADE-1301", "TRADE_CLOSED");

        verify(notificationService, never()).sendPaymentFailedNotification(any(PaymentRecord.class));
    }
}
