package com.yiyundao.compensation.modules.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.provider.SettlementCallbackResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementProvider;
import com.yiyundao.compensation.modules.payment.provider.SettlementRequest;
import com.yiyundao.compensation.modules.payment.provider.SettlementResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementStatus;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementProviderRoutingService;
import com.yiyundao.compensation.modules.payroll.service.PayrollDistributionService;
import com.yiyundao.compensation.service.EncryptionService;
import com.yiyundao.compensation.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementServiceImpl 路由测试")
class SettlementServiceImplTest {

    @Mock
    private SettlementProvider alipayProvider;
    @Mock
    private SettlementProvider yunzhanghuProvider;
    @Mock
    private PaymentRecordService paymentRecordService;
    @Mock
    private PaymentBatchService paymentBatchService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private PayrollBatchMapper payrollBatchMapper;
    @Mock
    private SettlementProviderRoutingService routingService;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private ObjectProvider<PayrollDistributionService> payrollDistributionServiceProvider;

    private SettlementServiceImpl settlementService;

    @BeforeEach
    void setUp() {
        when(alipayProvider.getProviderCode()).thenReturn("alipay");
        when(yunzhanghuProvider.getProviderCode()).thenReturn("yunzhanghu");

        settlementService = new SettlementServiceImpl(
                List.of(alipayProvider, yunzhanghuProvider),
                paymentRecordService,
                paymentBatchService,
                notificationService,
                employeeMapper,
                payrollBatchMapper,
                routingService,
                encryptionService,
                payrollDistributionServiceProvider
        );
        settlementService.initProviderMap();
    }

    @Test
    @DisplayName("按 payment_record.provider_code 路由单笔发放")
    void singleTransfer_shouldRouteByProviderCode() {
        PaymentRecord record = new PaymentRecord();
        record.setId(1L);
        record.setProviderCode("yunzhanghu");
        record.setAmount(new BigDecimal("88.00"));
        record.setCurrency("CNY");
        record.setRecipientName("张三");
        record.setRecipientAccount("13800000000");
        when(paymentRecordService.getById(1L)).thenReturn(record);
        when(yunzhanghuProvider.singleTransfer(any())).thenReturn(SettlementResult.builder()
                .success(true)
                .providerOrderNo("YZH_001")
                .providerTradeNo("T_001")
                .status(SettlementStatus.SUCCESS)
                .build());

        SettlementResult result = settlementService.singleTransfer(1L);

        assertTrue(result.isSuccess());
        verify(yunzhanghuProvider).singleTransfer(any(SettlementRequest.class));
        verify(alipayProvider, never()).singleTransfer(any());
    }

    @Test
    @DisplayName("provider_code 为空时按 payment_method 兜底路由")
    void singleTransfer_shouldFallbackToPaymentMethod() {
        PaymentRecord record = new PaymentRecord();
        record.setId(2L);
        record.setPaymentMethod("ALIPAY");
        record.setAmount(new BigDecimal("100.00"));
        record.setCurrency("CNY");
        record.setRecipientName("李四");
        record.setRecipientAccount("lisi@example.com");
        when(paymentRecordService.getById(2L)).thenReturn(record);
        when(alipayProvider.singleTransfer(any())).thenReturn(SettlementResult.builder()
                .success(true)
                .providerOrderNo("ALI_001")
                .providerTradeNo("TRADE_001")
                .status(SettlementStatus.SUCCESS)
                .build());

        settlementService.singleTransfer(2L);

        verify(alipayProvider).singleTransfer(any(SettlementRequest.class));
        verify(yunzhanghuProvider, never()).singleTransfer(any());
    }

    @Test
    @DisplayName("未知渠道编码时报错")
    void singleTransfer_shouldThrowWhenProviderUnsupported() {
        PaymentRecord record = new PaymentRecord();
        record.setId(3L);
        record.setProviderCode("bank_x");
        when(paymentRecordService.getById(3L)).thenReturn(record);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> settlementService.singleTransfer(3L));
        assertTrue(ex.getMessage().contains("不支持的结算渠道"));
    }

    @Test
    @DisplayName("回调验签失败时不进入渠道处理")
    void handleCallback_shouldStopWhenVerifyFailed() {
        when(alipayProvider.verifyCallback(any())).thenReturn(false);

        SettlementCallbackResult result = settlementService.handleCallback("alipay", Map.of("k", "v"));

        assertTrue(!result.isSuccess());
        verify(alipayProvider).verifyCallback(any());
        verify(alipayProvider, never()).handleCallback(any());
    }

    @Test
    @DisplayName("回调验签通过后进入渠道处理")
    void handleCallback_shouldHandleWhenVerifyPassed() {
        when(alipayProvider.verifyCallback(any())).thenReturn(true);
        when(alipayProvider.handleCallback(any())).thenReturn(SettlementCallbackResult.builder()
                .success(true)
                .bizNo("ALI_BIZ_001")
                .status(SettlementStatus.SUCCESS)
                .build());

        SettlementCallbackResult result = settlementService.handleCallback("alipay", Map.of("foo", "bar"));

        assertTrue(result.isSuccess());
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(alipayProvider).handleCallback(captor.capture());
        assertEquals("bar", captor.getValue().get("foo"));
    }

    @Test
    @DisplayName("主动对账可将 processing 收敛到 completed")
    void reconcileProcessingBatches_shouldConvergeToCompleted() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(10L);
        batch.setBatchNo("BATCH_001");
        batch.setStatus(BatchStatus.PROCESSING);

        PaymentRecord processingRecord = new PaymentRecord();
        processingRecord.setId(100L);
        processingRecord.setBatchNo("BATCH_001");
        processingRecord.setProviderCode("alipay");
        processingRecord.setProviderOrderNo("ALI_BIZ_001");
        processingRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PROCESSING);

        PaymentRecord successRecord = new PaymentRecord();
        successRecord.setId(100L);
        successRecord.setBatchNo("BATCH_001");
        successRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.SUCCESS);

        when(paymentBatchService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any())).thenReturn(List.of(batch));
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any())).thenReturn(List.of(processingRecord));
        when(alipayProvider.queryStatus("ALI_BIZ_001")).thenReturn(SettlementStatus.SUCCESS);
        when(paymentRecordService.getByBatchNo("BATCH_001", null)).thenReturn(List.of(successRecord));

        int scanned = settlementService.reconcileProcessingBatches(5, 50);

        assertEquals(1, scanned);
        ArgumentCaptor<PaymentRecord> recordCaptor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentRecordService).updateById(recordCaptor.capture());
        assertEquals(com.yiyundao.compensation.enums.PaymentStatus.SUCCESS, recordCaptor.getValue().getStatus());
        verify(paymentBatchService).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any());
    }
}
