package com.yiyundao.compensation.modules.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.SharedString;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollDistributionItemMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.dto.TransferValidationIssueDto;
import com.yiyundao.compensation.modules.payment.provider.SettlementCallbackResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementProvider;
import com.yiyundao.compensation.modules.payment.provider.SettlementRequest;
import com.yiyundao.compensation.modules.payment.provider.SettlementResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementStatus;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementProviderRoutingService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistributionItem;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentFailureService;
import com.yiyundao.compensation.modules.payroll.service.PayrollDistributionService;
import com.yiyundao.compensation.enums.PaymentType;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.PayrollDistributionItemStatus;
import com.yiyundao.compensation.service.EncryptionService;
import com.yiyundao.compensation.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
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
    private PayrollDistributionItemMapper payrollDistributionItemMapper;
    @Mock
    private SettlementProviderRoutingService routingService;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private ObjectProvider<PayrollDistributionService> payrollDistributionServiceProvider;
    @Mock
    private ObjectProvider<PayrollPaymentFailureService> payrollPaymentFailureServiceProvider;
    @Mock
    private PayrollPaymentFailureService payrollPaymentFailureService;
    @Mock
    private ObjectProvider<PlatformTransactionManager> transactionManagerProvider;

    private SettlementServiceImpl settlementService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, PaymentRecord.class.getName());
        assistant.setCurrentNamespace(PaymentRecord.class.getName());
        TableInfoHelper.initTableInfo(assistant, PaymentRecord.class);
    }

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
                payrollDistributionItemMapper,
                routingService,
                encryptionService,
                payrollDistributionServiceProvider,
                payrollPaymentFailureServiceProvider,
                transactionManagerProvider
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
        when(paymentRecordService.update(any())).thenReturn(true);
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
    @DisplayName("工资单笔转账前重算路由会将全职支付宝账户固定到支付宝渠道")
    void singleTransferShouldUseAlipayWhenRefreshingFullTimeAlipayRoute() {
        PaymentRecord record = salaryRecord(5L, null, PaymentStatus.PENDING);
        record.setEmployeeId(1001L);
        record.setProviderCode("yunzhanghu");
        PaymentRecord refreshedRecord = salaryRecord(5L, null, PaymentStatus.PENDING);
        refreshedRecord.setEmployeeId(1001L);
        refreshedRecord.setProviderCode("alipay");
        Employee employee = new Employee();
        employee.setId(1001L);
        employee.setName("张三");
        employee.setEmploymentType("full_time");
        employee.setEmail("zhangsan@example.com");
        employee.setSettlementAccountType("alipay");
        employee.setSettlementProviderCode("yunzhanghu");

        when(paymentRecordService.getById(5L)).thenReturn(record, record, refreshedRecord, refreshedRecord);
        when(employeeMapper.selectById(1001L)).thenReturn(employee);
        when(routingService.determineProvider(any(Employee.class), any())).thenReturn("yunzhanghu");
        when(paymentRecordService.update(any())).thenReturn(true);
        when(alipayProvider.singleTransfer(any())).thenReturn(SettlementResult.builder()
                .success(true)
                .providerOrderNo("ALI_ROUTE_5")
                .providerTradeNo("TRADE_ROUTE_5")
                .status(SettlementStatus.SUCCESS)
                .build());

        SettlementResult result = settlementService.singleTransfer(5L);

        assertTrue(result.isSuccess());
        verify(alipayProvider).singleTransfer(any(SettlementRequest.class));
        verify(yunzhanghuProvider, never()).singleTransfer(any());
        ArgumentCaptor<Wrapper<PaymentRecord>> updateCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(paymentRecordService, atLeastOnce()).update(updateCaptor.capture());
        List<String> updatedValues = updateCaptor.getAllValues().stream()
                .map(wrapper -> readField(wrapper, "paramNameValuePairs"))
                .filter(Map.class::isInstance)
                .flatMap(value -> ((Map<?, ?>) value).values().stream())
                .map(String::valueOf)
                .toList();
        assertThat(updatedValues).contains("alipay");
        assertThat(updatedValues).doesNotContain("yunzhanghu");
    }

    @Test
    @DisplayName("工资单笔转账前重算路由遇到不可解密账号会阻断渠道提交")
    void singleTransferShouldBlockUndecryptableEncryptedAccountDuringRouteRefresh() {
        PaymentRecord record = salaryRecord(6L, null, PaymentStatus.PENDING);
        record.setEmployeeId(1002L);
        PaymentRecord failedRecord = salaryRecord(6L, null, PaymentStatus.FAILED);
        failedRecord.setEmployeeId(1002L);
        failedRecord.setErrorCode("ACCOUNT_DECRYPT_FAILED");
        failedRecord.setErrorMsg("收款账号解密失败，请重新维护收款信息");
        Employee employee = new Employee();
        employee.setId(1002L);
        employee.setName("张三");
        employee.setEmploymentType("full_time");
        employee.setPhone("13800000000");
        employee.setSettlementAccountType("bank_card");
        employee.setSettlementAccount("ENC_BANK_VALUE");

        when(paymentRecordService.getById(6L)).thenReturn(record, failedRecord, failedRecord);
        when(employeeMapper.selectById(1002L)).thenReturn(employee);

        SettlementResult result = settlementService.singleTransfer(6L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("ACCOUNT_DECRYPT_FAILED");
        verify(alipayProvider, never()).singleTransfer(any());
        verify(yunzhanghuProvider, never()).singleTransfer(any());
    }

    @Test
    @DisplayName("工资单笔转账前重算路由保留历史明文账号兼容")
    void singleTransferShouldKeepLegacyPlainAccountWhenDecryptFailsDuringRouteRefresh() {
        PaymentRecord record = salaryRecord(7L, null, PaymentStatus.PENDING);
        record.setEmployeeId(1003L);
        PaymentRecord refreshedRecord = salaryRecord(7L, null, PaymentStatus.PENDING);
        refreshedRecord.setEmployeeId(1003L);
        refreshedRecord.setRecipientAccount("13800000000");
        refreshedRecord.setPaymentMethod("ALIPAY");
        refreshedRecord.setProviderCode("alipay");
        Employee employee = new Employee();
        employee.setId(1003L);
        employee.setName("张三");
        employee.setEmploymentType("full_time");
        employee.setSettlementAccountType("alipay");
        employee.setSettlementAccount("13800000000");

        when(paymentRecordService.getById(7L)).thenReturn(record, refreshedRecord, refreshedRecord);
        when(employeeMapper.selectById(1003L)).thenReturn(employee);
        when(encryptionService.decrypt("13800000000")).thenThrow(new RuntimeException("legacy plaintext"));
        when(routingService.determineProvider(any(Employee.class), any())).thenReturn("alipay");
        when(paymentRecordService.update(any())).thenReturn(true);
        when(alipayProvider.singleTransfer(any())).thenReturn(SettlementResult.builder()
                .success(true)
                .providerOrderNo("ALI_LEGACY_7")
                .providerTradeNo("TRADE_LEGACY_7")
                .status(SettlementStatus.SUCCESS)
                .build());

        SettlementResult result = settlementService.singleTransfer(7L);

        assertTrue(result.isSuccess());
        verify(alipayProvider).singleTransfer(any(SettlementRequest.class));
        verify(yunzhanghuProvider, never()).singleTransfer(any());
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
        when(paymentRecordService.update(any())).thenReturn(true);
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
    @DisplayName("单笔发放领取记录失败时不会重复提交渠道")
    void singleTransfer_shouldSkipProviderWhenRecordClaimFails() {
        PaymentRecord record = new PaymentRecord();
        record.setId(4L);
        record.setProviderCode("alipay");
        record.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PENDING);
        record.setAmount(new BigDecimal("100.00"));
        record.setCurrency("CNY");
        record.setRecipientName("王五");
        record.setRecipientAccount("wangwu@example.com");
        when(paymentRecordService.getById(4L)).thenReturn(record);
        when(paymentRecordService.update(any())).thenReturn(false);

        SettlementResult result = settlementService.singleTransfer(4L);

        assertTrue(result.isSuccess());
        assertEquals(SettlementStatus.PROCESSING, result.getStatus());
        verify(alipayProvider, never()).singleTransfer(any());
        verify(yunzhanghuProvider, never()).singleTransfer(any());
    }

    @Test
    @DisplayName("过期发放单关联的工资记录不会直接提交渠道")
    void singleTransferShouldBlockStalePayrollDistributionRecord() {
        PaymentRecord record = salaryRecord(4101L, "BATCH_RECORD_STALE", PaymentStatus.PENDING);
        PaymentBatch batch = payrollPaymentBatch("BATCH_RECORD_STALE", 4100L);
        PayrollDistributionService distributionService = org.mockito.Mockito.mock(PayrollDistributionService.class);
        PayrollDistribution distribution = payrollDistribution(4100L, 41L, 1);
        PayrollBatch payrollBatch = payrollBatch("BATCH_RECORD_STALE", 2, PayrollBatchStatus.PAY_PROCESSING);

        when(paymentRecordService.getById(4101L)).thenReturn(record, record);
        when(paymentBatchService.getByBatchNo("BATCH_RECORD_STALE")).thenReturn(batch);
        when(payrollDistributionServiceProvider.getIfAvailable()).thenReturn(distributionService);
        when(distributionService.getById(4100L)).thenReturn(distribution);
        when(payrollBatchMapper.selectById(41L)).thenReturn(payrollBatch);

        SettlementResult result = settlementService.singleTransfer(4101L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("发放单已过期");
        verify(paymentRecordService, never()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
        verify(alipayProvider, never()).singleTransfer(any());
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
    @DisplayName("失败记录重试会先重置为待处理再走单笔转账并刷新批次")
    void retryFailedRecord_shouldResetFailedRecordBeforeTransferAndSettleBatch() {
        PaymentRecord failedRecord = new PaymentRecord();
        failedRecord.setId(31L);
        failedRecord.setBatchNo("BATCH_RETRY_RECORD");
        failedRecord.setProviderCode("alipay");
        failedRecord.setPaymentType(com.yiyundao.compensation.enums.PaymentType.BONUS);
        failedRecord.setPaymentMethod("ALIPAY");
        failedRecord.setAmount(new BigDecimal("100.00"));
        failedRecord.setCurrency("CNY");
        failedRecord.setRecipientName("李四");
        failedRecord.setRecipientAccount("lisi@example.com");
        failedRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.FAILED);

        PaymentRecord pendingRecord = new PaymentRecord();
        pendingRecord.setId(31L);
        pendingRecord.setBatchNo("BATCH_RETRY_RECORD");
        pendingRecord.setProviderCode("alipay");
        pendingRecord.setPaymentType(com.yiyundao.compensation.enums.PaymentType.BONUS);
        pendingRecord.setPaymentMethod("ALIPAY");
        pendingRecord.setAmount(new BigDecimal("100.00"));
        pendingRecord.setCurrency("CNY");
        pendingRecord.setRecipientName("李四");
        pendingRecord.setRecipientAccount("lisi@example.com");
        pendingRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PENDING);

        PaymentRecord successRecord = new PaymentRecord();
        successRecord.setId(31L);
        successRecord.setBatchNo("BATCH_RETRY_RECORD");
        successRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.SUCCESS);

        PaymentBatch batch = new PaymentBatch();
        batch.setId(31L);
        batch.setBatchNo("BATCH_RETRY_RECORD");
        batch.setStatus(BatchStatus.FAILED);

        when(paymentRecordService.getById(31L)).thenReturn(failedRecord, pendingRecord, pendingRecord);
        when(paymentBatchService.getByBatchNo("BATCH_RETRY_RECORD")).thenReturn(batch, batch);
        when(paymentBatchService.update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any())).thenReturn(true);
        when(paymentRecordService.update(any())).thenReturn(true);
        when(alipayProvider.singleTransfer(any())).thenReturn(SettlementResult.builder()
                .success(true)
                .providerOrderNo("ALI_RETRY_31")
                .providerTradeNo("TRADE_RETRY_31")
                .status(SettlementStatus.SUCCESS)
                .build());
        when(paymentRecordService.getByBatchNo("BATCH_RETRY_RECORD", null)).thenReturn(List.of(successRecord));

        SettlementResult result = settlementService.retryFailedRecord(31L);

        assertTrue(result.isSuccess());
        verify(alipayProvider).singleTransfer(any(SettlementRequest.class));
        verify(paymentBatchService, atLeastOnce()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any());
        verify(notificationService).sendPaymentSuccessNotification(pendingRecord);

        ArgumentCaptor<UpdateWrapper<PaymentBatch>> batchUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(paymentBatchService, atLeastOnce()).update(batchUpdateCaptor.capture());
        assertThat(batchUpdateCaptor.getAllValues().stream()
                .map(wrapper -> {
                    wrapper.getSqlSegment();
                    return wrapper.getParamNameValuePairs().values();
                })
                .anyMatch(values -> values.containsAll(List.of(
                        BatchStatus.SUBMITTED.getCode(),
                        BatchStatus.APPROVED.getCode(),
                        BatchStatus.FAILED.getCode(),
                        BatchStatus.COMPLETED.getCode(),
                        PaymentBatchProcessStatus.PARTIAL_SUCCESS.getCode(),
                        BatchStatus.PROCESSING.getCode(),
                        PaymentBatchProcessStatus.PROCESSING.getCode()
                )))).isTrue();
    }

    @Test
    @DisplayName("失败记录重试前发现原渠道单已成功时不重复提交")
    void retryFailedRecord_shouldRecoverExistingProviderSuccessBeforeResubmitting() {
        PaymentRecord failedRecord = retryableRecord(35L, "BATCH_RETRY_LATE_SUCCESS",
                com.yiyundao.compensation.enums.PaymentStatus.FAILED);
        failedRecord.setProviderCode("yunzhanghu");
        failedRecord.setProviderOrderNo("YZH_RETRY_LATE_SUCCESS");
        failedRecord.setErrorCode("SUBMIT_TIMEOUT");
        failedRecord.setErrorMsg("submit timeout");

        PaymentRecord successRecord = retryableRecord(35L, "BATCH_RETRY_LATE_SUCCESS",
                com.yiyundao.compensation.enums.PaymentStatus.SUCCESS);

        PaymentBatch batch = new PaymentBatch();
        batch.setId(35L);
        batch.setBatchNo("BATCH_RETRY_LATE_SUCCESS");
        batch.setStatus(BatchStatus.FAILED);

        when(paymentRecordService.getById(35L)).thenReturn(failedRecord);
        when(paymentBatchService.getByBatchNo("BATCH_RETRY_LATE_SUCCESS")).thenReturn(batch, batch);
        when(yunzhanghuProvider.queryStatus("YZH_RETRY_LATE_SUCCESS")).thenReturn(SettlementStatus.SUCCESS);
        when(paymentRecordService.update(any())).thenReturn(true);
        when(paymentRecordService.getByBatchNo("BATCH_RETRY_LATE_SUCCESS", null)).thenReturn(List.of(successRecord));

        SettlementResult result = settlementService.retryFailedRecord(35L);

        assertTrue(result.isSuccess());
        assertEquals(SettlementStatus.SUCCESS, result.getStatus());
        assertThat(failedRecord.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(failedRecord.getProviderOrderNo()).isEqualTo("YZH_RETRY_LATE_SUCCESS");
        verify(yunzhanghuProvider, never()).singleTransfer(any());
        verify(notificationService).sendPaymentSuccessNotification(failedRecord);
        verify(paymentBatchService, atLeastOnce()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any());
    }

    @Test
    @DisplayName("失败记录已有渠道单且仍处理中时禁止重试")
    void retryFailedRecord_shouldRejectExistingProviderOrderStillProcessing() {
        PaymentRecord failedRecord = retryableRecord(36L, "BATCH_RETRY_STILL_PROCESSING",
                com.yiyundao.compensation.enums.PaymentStatus.FAILED);
        failedRecord.setProviderCode("yunzhanghu");
        failedRecord.setProviderOrderNo("YZH_RETRY_STILL_PROCESSING");

        PaymentBatch batch = new PaymentBatch();
        batch.setId(36L);
        batch.setBatchNo("BATCH_RETRY_STILL_PROCESSING");
        batch.setStatus(BatchStatus.FAILED);

        when(paymentRecordService.getById(36L)).thenReturn(failedRecord);
        when(paymentBatchService.getByBatchNo("BATCH_RETRY_STILL_PROCESSING")).thenReturn(batch);
        when(yunzhanghuProvider.queryStatus("YZH_RETRY_STILL_PROCESSING")).thenReturn(SettlementStatus.PROCESSING);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> settlementService.retryFailedRecord(36L));

        assertTrue(ex.getMessage().contains("渠道订单仍在处理中"));
        verify(paymentRecordService, never()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
        verify(yunzhanghuProvider, never()).singleTransfer(any());
    }

    @Test
    @DisplayName("失败记录只有渠道交易号时禁止重试，避免重复打款")
    void retryFailedRecordShouldRejectExistingProviderTradeWithoutOrderNo() {
        PaymentRecord failedRecord = retryableRecord(38L, "BATCH_RETRY_TRADE_ONLY",
                com.yiyundao.compensation.enums.PaymentStatus.FAILED);
        failedRecord.setProviderCode("yunzhanghu");
        failedRecord.setProviderTradeNo("YZH_TRADE_ONLY_38");

        PaymentBatch batch = new PaymentBatch();
        batch.setId(38L);
        batch.setBatchNo("BATCH_RETRY_TRADE_ONLY");
        batch.setStatus(BatchStatus.FAILED);

        when(paymentRecordService.getById(38L)).thenReturn(failedRecord);
        when(paymentBatchService.getByBatchNo("BATCH_RETRY_TRADE_ONLY")).thenReturn(batch);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> settlementService.retryFailedRecord(38L));

        assertThat(ex.getMessage()).contains("已提交渠道交易号");
        verify(paymentRecordService, never()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
        verify(yunzhanghuProvider, never()).queryStatus(any());
        verify(yunzhanghuProvider, never()).singleTransfer(any());
    }

    @Test
    @DisplayName("过期发放单关联的工资记录级重试不会重新提交渠道")
    void retryFailedRecordShouldBlockStalePayrollDistributionRecord() {
        PaymentRecord failedRecord = salaryRecord(3701L, "BATCH_RETRY_STALE_DISTRIBUTION", PaymentStatus.FAILED);
        PaymentBatch batch = payrollPaymentBatch("BATCH_RETRY_STALE_DISTRIBUTION", 3700L);
        batch.setStatus(BatchStatus.FAILED);
        PayrollDistributionService distributionService = org.mockito.Mockito.mock(PayrollDistributionService.class);
        PayrollDistribution distribution = payrollDistribution(3700L, 37L, 1);
        PayrollBatch payrollBatch = payrollBatch("BATCH_RETRY_STALE_DISTRIBUTION", 2, PayrollBatchStatus.PAY_FAILED);

        when(paymentRecordService.getById(3701L)).thenReturn(failedRecord);
        when(paymentBatchService.getByBatchNo("BATCH_RETRY_STALE_DISTRIBUTION")).thenReturn(batch);
        when(payrollDistributionServiceProvider.getIfAvailable()).thenReturn(distributionService);
        when(distributionService.getById(3700L)).thenReturn(distribution);
        when(payrollBatchMapper.selectById(37L)).thenReturn(payrollBatch);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> settlementService.retryFailedRecord(3701L));

        assertThat(ex.getMessage()).contains("发放单已过期");
        verify(paymentRecordService, never()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
        verify(alipayProvider, never()).singleTransfer(any());
        verify(yunzhanghuProvider, never()).singleTransfer(any());
    }

    @Test
    @DisplayName("工资明细达到重试上限时拒绝记录级重试")
    void retryFailedRecordShouldRejectDistributionItemAtRetryLimit() {
        PaymentRecord failedRecord = salaryRecord(3702L, "BATCH_RETRY_LIMIT", PaymentStatus.FAILED);
        PaymentBatch batch = payrollPaymentBatch("BATCH_RETRY_LIMIT", 3702L);
        batch.setStatus(BatchStatus.FAILED);

        PayrollDistributionService distributionService = org.mockito.Mockito.mock(PayrollDistributionService.class);
        PayrollDistribution distribution = payrollDistribution(3702L, 37L, 1);
        distribution.setRetryLimit(1);
        PayrollDistributionItem item = new PayrollDistributionItem();
        item.setId(3703L);
        item.setDistributionId(3702L);
        item.setPaymentRecordId(3702L);
        item.setItemStatus(PayrollDistributionItemStatus.FAILED);
        item.setRetryCount(1);

        when(paymentRecordService.getById(3702L)).thenReturn(failedRecord);
        when(paymentBatchService.getByBatchNo("BATCH_RETRY_LIMIT")).thenReturn(batch);
        when(payrollDistributionServiceProvider.getIfAvailable()).thenReturn(distributionService);
        when(distributionService.getById(3702L)).thenReturn(distribution);
        when(distributionService.listActiveItems(3702L)).thenReturn(List.of(item));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> settlementService.retryFailedRecord(3702L));

        assertThat(ex.getMessage()).contains("达到最大重试次数: 1");
        verify(payrollDistributionItemMapper, never()).update(any(), any());
        verify(paymentRecordService, never()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
        verify(alipayProvider, never()).singleTransfer(any());
        verify(yunzhanghuProvider, never()).singleTransfer(any());
    }

    @Test
    @DisplayName("失败记录重试进入处理中时同步薪酬批次为支付处理中")
    void retryFailedRecord_shouldMarkPayrollBatchProcessingWhenProviderReturnsProcessing() {
        PaymentRecord failedRecord = retryableRecord(34L, "BATCH_RETRY_PROCESSING",
                com.yiyundao.compensation.enums.PaymentStatus.FAILED);
        PaymentRecord pendingRecord = retryableRecord(34L, "BATCH_RETRY_PROCESSING",
                com.yiyundao.compensation.enums.PaymentStatus.PENDING);
        PaymentRecord processingRecord = retryableRecord(34L, "BATCH_RETRY_PROCESSING",
                com.yiyundao.compensation.enums.PaymentStatus.PROCESSING);

        PaymentBatch batch = new PaymentBatch();
        batch.setId(34L);
        batch.setBatchNo("BATCH_RETRY_PROCESSING");
        batch.setStatus(BatchStatus.FAILED);

        when(paymentRecordService.getById(34L)).thenReturn(failedRecord, pendingRecord, pendingRecord);
        when(paymentBatchService.getByBatchNo("BATCH_RETRY_PROCESSING")).thenReturn(batch, batch);
        when(paymentBatchService.update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any())).thenReturn(true);
        when(paymentRecordService.update(any())).thenReturn(true);
        when(alipayProvider.singleTransfer(any())).thenReturn(SettlementResult.builder()
                .success(true)
                .providerOrderNo("ALI_PROCESSING_34")
                .providerTradeNo("TRADE_PROCESSING_34")
                .status(SettlementStatus.PROCESSING)
                .build());
        when(paymentRecordService.getByBatchNo("BATCH_RETRY_PROCESSING", null)).thenReturn(List.of(processingRecord));

        SettlementResult result = settlementService.retryFailedRecord(34L);

        assertEquals(SettlementStatus.PROCESSING, result.getStatus());
        verify(payrollBatchMapper).update(org.mockito.ArgumentMatchers.eq(null),
                org.mockito.ArgumentMatchers.<Wrapper<com.yiyundao.compensation.modules.payroll.entity.PayrollBatch>>any());
    }

    @Test
    @DisplayName("记录级重试领取批次失败时不把薪资批次同步为处理中")
    void retryFailedRecordShouldNotSyncPayrollProcessingWhenBatchClaimFails() {
        PaymentRecord failedRecord = retryableRecord(39L, "BATCH_RETRY_BATCH_CLAIM_FAIL",
                com.yiyundao.compensation.enums.PaymentStatus.FAILED);

        PaymentBatch batch = new PaymentBatch();
        batch.setId(39L);
        batch.setBatchNo("BATCH_RETRY_BATCH_CLAIM_FAIL");
        batch.setStatus(BatchStatus.FAILED);

        when(paymentRecordService.getById(39L)).thenReturn(failedRecord);
        when(paymentBatchService.getByBatchNo("BATCH_RETRY_BATCH_CLAIM_FAIL")).thenReturn(batch, batch);
        when(paymentBatchService.update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any()))
                .thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> settlementService.retryFailedRecord(39L));

        assertThat(ex.getMessage()).contains("支付批次状态已变更");
        verify(paymentRecordService, never()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
        verify(alipayProvider, never()).singleTransfer(any());
        verify(payrollBatchMapper, never()).update(org.mockito.ArgumentMatchers.eq(null),
                org.mockito.ArgumentMatchers.<Wrapper<com.yiyundao.compensation.modules.payroll.entity.PayrollBatch>>any());
    }

    @Test
    @DisplayName("非失败记录不能走记录级重试")
    void retryFailedRecord_shouldRejectNonFailedRecord() {
        PaymentRecord pendingRecord = new PaymentRecord();
        pendingRecord.setId(32L);
        pendingRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PENDING);
        when(paymentRecordService.getById(32L)).thenReturn(pendingRecord);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> settlementService.retryFailedRecord(32L));

        assertTrue(ex.getMessage().contains("仅失败或已取消"));
        verify(alipayProvider, never()).singleTransfer(any());
    }

    @Test
    @DisplayName("失败记录重置未命中时不能污染批次状态")
    void retryFailedRecord_shouldNotMarkBatchProcessingWhenResetFails() {
        PaymentRecord failedRecord = new PaymentRecord();
        failedRecord.setId(33L);
        failedRecord.setBatchNo("BATCH_RETRY_RESET_FAIL");
        failedRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.FAILED);

        PaymentBatch batch = new PaymentBatch();
        batch.setId(33L);
        batch.setBatchNo("BATCH_RETRY_RESET_FAIL");
        batch.setStatus(BatchStatus.FAILED);

        when(paymentRecordService.getById(33L)).thenReturn(failedRecord);
        when(paymentBatchService.getByBatchNo("BATCH_RETRY_RESET_FAIL")).thenReturn(batch);
        when(paymentBatchService.update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any())).thenReturn(true, true);
        when(paymentRecordService.update(any())).thenReturn(false);
        when(paymentRecordService.getByBatchNo("BATCH_RETRY_RESET_FAIL", null)).thenReturn(List.of(failedRecord));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> settlementService.retryFailedRecord(33L));

        assertTrue(ex.getMessage().contains("状态已变更"));
        verify(alipayProvider, never()).singleTransfer(any());
        ArgumentCaptor<UpdateWrapper<com.yiyundao.compensation.modules.payroll.entity.PayrollBatch>> payrollUpdateCaptor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollBatchMapper).update(org.mockito.ArgumentMatchers.eq(null), payrollUpdateCaptor.capture());
        payrollUpdateCaptor.getValue().getSqlSegment();
        String statusSetParam = payrollUpdateCaptor.getValue().getSqlSet()
                .replaceFirst("(?s).*`?status`?\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}.*", "$1");
        assertThat(statusSetParam).isNotEqualTo(payrollUpdateCaptor.getValue().getSqlSet());
        assertThat(payrollUpdateCaptor.getValue().getParamNameValuePairs().get(statusSetParam))
                .isEqualTo(PayrollBatchStatus.PAY_FAILED.getCode());
    }

    @Test
    @DisplayName("持久化预检失败时会收敛批次和薪资状态")
    void validateBatchForTransferShouldSettleBatchWhenPersistingBlockedRecords() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(41L);
        batch.setBatchNo("BATCH_VALIDATE_BLOCKED");
        batch.setDistributionId(4100L);
        batch.setStatus(BatchStatus.SUBMITTED);

        PaymentRecord pendingRecord = new PaymentRecord();
        pendingRecord.setId(4101L);
        pendingRecord.setBatchNo("BATCH_VALIDATE_BLOCKED");
        pendingRecord.setEmployeeId(5001L);
        pendingRecord.setPaymentType(com.yiyundao.compensation.enums.PaymentType.SALARY);
        pendingRecord.setStatus(PaymentStatus.PENDING);
        pendingRecord.setProviderCode("alipay");
        pendingRecord.setPaymentMethod("ALIPAY");
        pendingRecord.setAmount(new BigDecimal("100.00"));
        pendingRecord.setCurrency("CNY");
        pendingRecord.setRecipientName("张三");
        pendingRecord.setRecipientAccount("");

        PaymentRecord failedRecord = new PaymentRecord();
        failedRecord.setId(4101L);
        failedRecord.setBatchNo("BATCH_VALIDATE_BLOCKED");
        failedRecord.setStatus(PaymentStatus.FAILED);

        PayrollDistributionService distributionService = org.mockito.Mockito.mock(PayrollDistributionService.class);
        when(paymentBatchService.getByBatchNo("BATCH_VALIDATE_BLOCKED")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("BATCH_VALIDATE_BLOCKED", PaymentStatus.PENDING))
                .thenReturn(List.of(pendingRecord));
        when(paymentRecordService.getById(4101L)).thenReturn(pendingRecord, pendingRecord);
        when(paymentRecordService.update(any())).thenReturn(true);
        when(paymentRecordService.getByBatchNo("BATCH_VALIDATE_BLOCKED", null)).thenReturn(List.of(failedRecord));
        when(payrollDistributionServiceProvider.getIfAvailable()).thenReturn(distributionService);

        var validation = settlementService.validateBatchForTransfer("BATCH_VALIDATE_BLOCKED", true);

        assertTrue(!validation.getPass());
        assertEquals(1, validation.getBlockedCount());
        verify(paymentBatchService).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any());
        ArgumentCaptor<UpdateWrapper<PayrollBatch>> payrollBatchWrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(payrollBatchMapper).update(org.mockito.ArgumentMatchers.eq(null), payrollBatchWrapperCaptor.capture());
        UpdateWrapper<PayrollBatch> payrollBatchWrapper = payrollBatchWrapperCaptor.getValue();
        assertThat(payrollBatchWrapper.getSqlSegment()).contains("status IN");
        assertThat(payrollBatchWrapper.getParamNameValuePairs().values())
                .contains(PayrollBatchStatus.PAY_FAILED.getCode(),
                        PayrollBatchStatus.PAY_PROCESSING.getCode());
        verify(distributionService).syncFromPaymentBatch(batch);
    }

    @Test
    @DisplayName("持久化预检失败时只标记风险记录，不取消同批次健康待发记录")
    void validateBatchForTransferShouldKeepHealthyPendingRecordsWhenPersistingBlockedRecords() {
        PaymentBatch batch = payrollPaymentBatch("BATCH_VALIDATE_PARTIAL_BLOCKED", 4102L);
        PayrollDistributionService distributionService = org.mockito.Mockito.mock(PayrollDistributionService.class);
        PayrollDistribution distribution = payrollDistribution(4102L, 4106L, 1);
        PayrollBatch payrollBatch = payrollBatch("BATCH_VALIDATE_PARTIAL_BLOCKED", 1, PayrollBatchStatus.PAY_PROCESSING);

        PaymentRecord invalidRecord = salaryRecord(4103L, "BATCH_VALIDATE_PARTIAL_BLOCKED", PaymentStatus.PENDING);
        invalidRecord.setRecipientAccount("");
        PaymentRecord validRecord = salaryRecord(4104L, "BATCH_VALIDATE_PARTIAL_BLOCKED", PaymentStatus.PENDING);

        PaymentRecord failedInvalidRecord = salaryRecord(4103L, "BATCH_VALIDATE_PARTIAL_BLOCKED", PaymentStatus.FAILED);
        PaymentRecord stillPendingRecord = salaryRecord(4104L, "BATCH_VALIDATE_PARTIAL_BLOCKED", PaymentStatus.PENDING);

        when(paymentBatchService.getByBatchNo("BATCH_VALIDATE_PARTIAL_BLOCKED")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("BATCH_VALIDATE_PARTIAL_BLOCKED", PaymentStatus.PENDING))
                .thenReturn(List.of(invalidRecord, validRecord));
        when(paymentRecordService.getById(4103L)).thenReturn(invalidRecord, invalidRecord);
        when(paymentRecordService.getById(4104L)).thenReturn(validRecord, validRecord);
        when(paymentRecordService.update(any())).thenReturn(true);
        when(paymentRecordService.getByBatchNo("BATCH_VALIDATE_PARTIAL_BLOCKED", null))
                .thenReturn(List.of(failedInvalidRecord, stillPendingRecord));
        when(payrollDistributionServiceProvider.getIfAvailable()).thenReturn(distributionService);
        when(distributionService.getById(4102L)).thenReturn(distribution);
        when(payrollBatchMapper.selectById(4106L)).thenReturn(payrollBatch);

        var validation = settlementService.validateBatchForTransfer("BATCH_VALIDATE_PARTIAL_BLOCKED", true);

        assertThat(validation.getPass()).isFalse();
        assertThat(validation.getBlockedCount()).isEqualTo(1);
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.SUBMITTED);

        ArgumentCaptor<UpdateWrapper<PaymentRecord>> recordUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(paymentRecordService, atLeastOnce()).update(recordUpdateCaptor.capture());
        assertThat(recordUpdateCaptor.getAllValues()).noneMatch(wrapper -> {
            String sqlSegment = wrapper.getSqlSegment();
            return sqlSegment.contains("batch_no =")
                    && sqlSegment.contains("status =")
                    && wrapper.getParamNameValuePairs().values().contains(PaymentStatus.CANCELLED.getCode());
        });

        ArgumentCaptor<UpdateWrapper<PaymentBatch>> batchUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(paymentBatchService).update(batchUpdateCaptor.capture());
        UpdateWrapper<PaymentBatch> batchUpdate = batchUpdateCaptor.getValue();
        batchUpdate.getSqlSegment();
        assertThat(batchUpdate.getParamNameValuePairs().values())
                .contains(BatchStatus.SUBMITTED.getCode(),
                        com.yiyundao.compensation.enums.PaymentBatchProcessStatus.SUBMITTED.getCode());
        verify(distributionService).syncFromPaymentBatch(batch);
    }

    @Test
    @DisplayName("预检遇到缺失员工ID的待处理记录时按记录自身收款字段校验而不是NPE")
    void validateBatchForTransferShouldHandlePendingRecordWithoutEmployeeId() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(4110L);
        batch.setBatchNo("BATCH_VALIDATE_MISSING_EMPLOYEE_ID");
        batch.setPaymentType(PaymentType.BONUS);
        batch.setStatus(BatchStatus.SUBMITTED);

        PaymentRecord record = new PaymentRecord();
        record.setId(4111L);
        record.setBatchNo("BATCH_VALIDATE_MISSING_EMPLOYEE_ID");
        record.setPaymentType(PaymentType.SALARY);
        record.setStatus(PaymentStatus.PENDING);
        record.setProviderCode("alipay");
        record.setPaymentMethod("ALIPAY");
        record.setAmount(new BigDecimal("100.00"));
        record.setCurrency("CNY");
        record.setRecipientName("张三");
        record.setRecipientAccount("zhangsan@example.com");

        when(paymentBatchService.getByBatchNo("BATCH_VALIDATE_MISSING_EMPLOYEE_ID")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("BATCH_VALIDATE_MISSING_EMPLOYEE_ID", PaymentStatus.PENDING))
                .thenReturn(List.of(record));
        when(paymentRecordService.getById(4111L)).thenReturn(record, record);

        var validation = settlementService.validateBatchForTransfer("BATCH_VALIDATE_MISSING_EMPLOYEE_ID", false);

        assertThat(validation.getPass()).isTrue();
        assertThat(validation.getBlockedCount()).isZero();
    }

    @Test
    @DisplayName("预检发现待处理记录已有渠道标识时禁止再次下单")
    void validateBatchForTransferShouldBlockPendingRecordWithProviderOrder() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(4111L);
        batch.setBatchNo("BATCH_VALIDATE_PROVIDER_ORDER");
        batch.setPaymentType(PaymentType.BONUS);
        batch.setStatus(BatchStatus.SUBMITTED);

        PaymentRecord record = retryableRecord(4112L, "BATCH_VALIDATE_PROVIDER_ORDER", PaymentStatus.PENDING);
        record.setProviderOrderNo("ALI_PENDING_ORDER_4112");

        when(paymentBatchService.getByBatchNo("BATCH_VALIDATE_PROVIDER_ORDER")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("BATCH_VALIDATE_PROVIDER_ORDER", PaymentStatus.PENDING))
                .thenReturn(List.of(record));
        when(paymentRecordService.getById(4112L)).thenReturn(record);

        var validation = settlementService.validateBatchForTransfer("BATCH_VALIDATE_PROVIDER_ORDER", false);

        assertThat(validation.getPass()).isFalse();
        assertThat(validation.getBlockedRecords()).singleElement()
                .extracting(TransferValidationIssueDto::getErrorCode)
                .isEqualTo("PROVIDER_ORDER_EXISTS");
        verify(alipayProvider, never()).singleTransfer(any());
        verify(yunzhanghuProvider, never()).singleTransfer(any());
    }

    @Test
    @DisplayName("预检遇到已成功记录时不会刷新收款路由或覆盖状态")
    void validateBatchForTransferShouldNotRefreshRouteForTerminalRecord() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(4112L);
        batch.setBatchNo("BATCH_VALIDATE_TERMINAL_RECORD");
        batch.setPaymentType(PaymentType.SALARY);
        batch.setStatus(BatchStatus.PROCESSING);

        PaymentRecord stalePendingRecord = salaryRecord(4113L, "BATCH_VALIDATE_TERMINAL_RECORD", PaymentStatus.PENDING);
        PaymentRecord successRecord = salaryRecord(4113L, "BATCH_VALIDATE_TERMINAL_RECORD", PaymentStatus.SUCCESS);

        when(paymentBatchService.getByBatchNo("BATCH_VALIDATE_TERMINAL_RECORD")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("BATCH_VALIDATE_TERMINAL_RECORD", PaymentStatus.PENDING))
                .thenReturn(List.of(stalePendingRecord));
        when(paymentRecordService.getById(4113L)).thenReturn(successRecord);

        var validation = settlementService.validateBatchForTransfer("BATCH_VALIDATE_TERMINAL_RECORD", false);

        assertThat(validation.getPass()).isFalse();
        assertThat(validation.getBlockedCount()).isEqualTo(1);
        assertThat(validation.getBlockedRecords().get(0).getErrorCode()).isEqualTo("STATUS_INVALID");
        verify(employeeMapper, never()).selectById(any());
        verify(paymentRecordService, never()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
    }

    @Test
    @DisplayName("预检会拦截过期发放单关联的工资支付批次")
    void validateBatchForTransferShouldBlockStalePayrollDistributionRevision() {
        PaymentBatch batch = payrollPaymentBatch("BATCH_VALIDATE_STALE", 4200L);
        PayrollDistributionService distributionService = org.mockito.Mockito.mock(PayrollDistributionService.class);
        PayrollDistribution distribution = payrollDistribution(4200L, 42L, 1);
        PayrollBatch payrollBatch = payrollBatch("BATCH_VALIDATE_STALE", 2, PayrollBatchStatus.PAY_PROCESSING);

        when(paymentBatchService.getByBatchNo("BATCH_VALIDATE_STALE")).thenReturn(batch);
        when(payrollDistributionServiceProvider.getIfAvailable()).thenReturn(distributionService);
        when(distributionService.getById(4200L)).thenReturn(distribution);
        when(payrollBatchMapper.selectById(42L)).thenReturn(payrollBatch);

        var validation = settlementService.validateBatchForTransfer("BATCH_VALIDATE_STALE", false);

        assertThat(validation.getPass()).isFalse();
        assertThat(validation.getWarnings()).containsExactly("支付批次关联的薪资发放单已过期，禁止启动工资转账");
        verify(paymentRecordService, never()).getByBatchNo("BATCH_VALIDATE_STALE", PaymentStatus.PENDING);
    }

    private PaymentRecord retryableRecord(Long id,
                                          String batchNo,
                                          com.yiyundao.compensation.enums.PaymentStatus status) {
        PaymentRecord record = new PaymentRecord();
        record.setId(id);
        record.setBatchNo(batchNo);
        record.setProviderCode("alipay");
        record.setPaymentType(com.yiyundao.compensation.enums.PaymentType.BONUS);
        record.setPaymentMethod("ALIPAY");
        record.setAmount(new BigDecimal("100.00"));
        record.setCurrency("CNY");
        record.setRecipientName("李四");
        record.setRecipientAccount("lisi@example.com");
        record.setStatus(status);
        return record;
    }

    private PaymentRecord salaryRecord(Long id, String batchNo, PaymentStatus status) {
        PaymentRecord record = new PaymentRecord();
        record.setId(id);
        record.setBatchNo(batchNo);
        record.setProviderCode("alipay");
        record.setPaymentType(PaymentType.SALARY);
        record.setPaymentMethod("ALIPAY");
        record.setAmount(new BigDecimal("100.00"));
        record.setCurrency("CNY");
        record.setRecipientName("张三");
        record.setRecipientAccount("zhangsan@example.com");
        record.setStatus(status);
        return record;
    }

    private PaymentBatch payrollPaymentBatch(String batchNo, Long distributionId) {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(distributionId);
        batch.setBatchNo(batchNo);
        batch.setPaymentType(PaymentType.SALARY);
        batch.setDistributionId(distributionId);
        batch.setStatus(BatchStatus.SUBMITTED);
        return batch;
    }

    private PayrollDistribution payrollDistribution(Long distributionId, Long batchId, Integer batchRevision) {
        PayrollDistribution distribution = new PayrollDistribution();
        distribution.setId(distributionId);
        distribution.setBatchId(batchId);
        distribution.setBatchRevision(batchRevision);
        return distribution;
    }

    private PayrollBatch payrollBatch(String paymentBatchNo, Integer batchRevision, PayrollBatchStatus status) {
        PayrollBatch payrollBatch = new PayrollBatch();
        payrollBatch.setPaymentBatchNo(paymentBatchNo);
        payrollBatch.setBatchRevision(batchRevision);
        payrollBatch.setStatus(status);
        return payrollBatch;
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
    @DisplayName("非支付宝回调成功后会发送支付成功通知")
    void handleCallback_shouldNotifyEmployeeForNonAlipayProvider() {
        when(yunzhanghuProvider.verifyCallback(any())).thenReturn(true);
        when(yunzhanghuProvider.handleCallback(any())).thenReturn(SettlementCallbackResult.builder()
                .success(true)
                .bizNo("YZH_BIZ_001")
                .status(SettlementStatus.SUCCESS)
                .build());

        PaymentRecord record = new PaymentRecord();
        record.setId(2101L);
        record.setBatchNo("BATCH_YZH_CALLBACK");
        record.setProviderCode("yunzhanghu");
        when(paymentRecordService.getByProviderOrderNo("yunzhanghu", "YZH_BIZ_001")).thenReturn(record);

        PaymentBatch batch = new PaymentBatch();
        batch.setId(21L);
        batch.setBatchNo("BATCH_YZH_CALLBACK");
        batch.setStatus(BatchStatus.PROCESSING);
        when(paymentBatchService.getByBatchNo("BATCH_YZH_CALLBACK")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("BATCH_YZH_CALLBACK", null)).thenReturn(List.of(record));
        when(paymentRecordService.update(any())).thenReturn(true);

        SettlementCallbackResult result = settlementService.handleCallback("yunzhanghu", Map.of("foo", "bar"));

        assertTrue(result.isSuccess());
        verify(notificationService).sendPaymentSuccessNotification(record);
    }

    @Test
    @DisplayName("迟到失败回调命中已成功记录时不会发送失败通知")
    void handleCallback_shouldNotNotifyLateFailedCallbackWhenRecordAlreadySuccess() {
        when(yunzhanghuProvider.verifyCallback(any())).thenReturn(true);
        when(yunzhanghuProvider.handleCallback(any())).thenReturn(SettlementCallbackResult.builder()
                .success(true)
                .bizNo("YZH_BIZ_DONE")
                .status(SettlementStatus.FAILED)
                .errorMsg("late failed callback")
                .build());

        PaymentRecord record = new PaymentRecord();
        record.setId(2102L);
        record.setBatchNo("BATCH_YZH_DONE");
        record.setProviderCode("yunzhanghu");
        record.setStatus(com.yiyundao.compensation.enums.PaymentStatus.SUCCESS);
        when(paymentRecordService.getByProviderOrderNo("yunzhanghu", "YZH_BIZ_DONE")).thenReturn(record);
        when(paymentRecordService.update(any())).thenReturn(false);

        PaymentBatch batch = new PaymentBatch();
        batch.setId(22L);
        batch.setBatchNo("BATCH_YZH_DONE");
        batch.setStatus(BatchStatus.COMPLETED);
        when(paymentBatchService.getByBatchNo("BATCH_YZH_DONE")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("BATCH_YZH_DONE", null)).thenReturn(List.of(record));

        SettlementCallbackResult result = settlementService.handleCallback("yunzhanghu", Map.of("foo", "bar"));

        assertTrue(result.isSuccess());
        verify(notificationService, never()).sendPaymentFailedNotification(any(PaymentRecord.class));
    }

    @Test
    @DisplayName("重复成功回调未抢占通知标记时不会重复发送到账通知")
    void handleCallback_shouldNotNotifyDuplicateSuccessCallbackWhenNotificationAlreadyClaimed() {
        when(yunzhanghuProvider.verifyCallback(any())).thenReturn(true);
        when(yunzhanghuProvider.handleCallback(any())).thenReturn(SettlementCallbackResult.builder()
                .success(true)
                .bizNo("YZH_BIZ_SUCCESS_DUP")
                .status(SettlementStatus.SUCCESS)
                .build());

        PaymentRecord record = new PaymentRecord();
        record.setId(2103L);
        record.setBatchNo("BATCH_YZH_SUCCESS_DUP");
        record.setProviderCode("yunzhanghu");
        record.setStatus(com.yiyundao.compensation.enums.PaymentStatus.SUCCESS);
        when(paymentRecordService.getByProviderOrderNo("yunzhanghu", "YZH_BIZ_SUCCESS_DUP")).thenReturn(record);
        when(paymentRecordService.update(any())).thenReturn(false);

        PaymentBatch batch = new PaymentBatch();
        batch.setId(23L);
        batch.setBatchNo("BATCH_YZH_SUCCESS_DUP");
        batch.setStatus(BatchStatus.COMPLETED);
        when(paymentBatchService.getByBatchNo("BATCH_YZH_SUCCESS_DUP")).thenReturn(batch);
        when(paymentRecordService.getByBatchNo("BATCH_YZH_SUCCESS_DUP", null)).thenReturn(List.of(record));

        SettlementCallbackResult result = settlementService.handleCallback("yunzhanghu", Map.of("foo", "bar"));

        assertTrue(result.isSuccess());
        verify(notificationService, never()).sendPaymentSuccessNotification(any(PaymentRecord.class));
    }

    @Test
    @DisplayName("渠道回调处理失败时不刷新批次状态")
    void handleCallback_shouldNotRefreshBatchWhenProviderRejectsCallback() {
        when(alipayProvider.verifyCallback(any())).thenReturn(true);
        when(alipayProvider.handleCallback(any())).thenReturn(SettlementCallbackResult.builder()
                .success(false)
                .bizNo("ALI_BIZ_UNKNOWN")
                .status(SettlementStatus.PROCESSING)
                .errorMsg("未知支付宝回调状态")
                .build());

        SettlementCallbackResult result = settlementService.handleCallback("alipay", Map.of("foo", "bar"));

        assertTrue(!result.isSuccess());
        verify(paymentRecordService, never()).getByProviderOrderNo(any(), any());
        verify(paymentBatchService, never()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any());
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

        when(paymentBatchService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any()))
                .thenReturn(List.of(batch), List.of());
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any())).thenReturn(List.of(processingRecord));
        when(alipayProvider.queryStatus("ALI_BIZ_001")).thenReturn(SettlementStatus.SUCCESS);
        when(paymentRecordService.getByBatchNo("BATCH_001", null)).thenReturn(List.of(successRecord));

        int scanned = settlementService.reconcileProcessingBatches(5, 50);

        assertEquals(1, scanned);
        verify(paymentRecordService).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
        verify(paymentBatchService).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any());
    }

    @Test
    @DisplayName("主动对账会推进 processing 批次中的 pending 记录，避免批次卡住")
    void reconcileProcessingBatches_shouldAlsoProcessPendingRecordsInProcessingBatch() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(11L);
        batch.setBatchNo("BATCH_002");
        batch.setStatus(BatchStatus.PROCESSING);

        PaymentRecord pendingRecord = new PaymentRecord();
        pendingRecord.setId(101L);
        pendingRecord.setBatchNo("BATCH_002");
        pendingRecord.setProviderCode("alipay");
        pendingRecord.setProviderOrderNo("ALI_BIZ_002");
        pendingRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PENDING);

        PaymentRecord successRecord = new PaymentRecord();
        successRecord.setId(101L);
        successRecord.setBatchNo("BATCH_002");
        successRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.SUCCESS);

        when(paymentBatchService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any()))
                .thenReturn(List.of(batch), List.of());
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any())).thenReturn(List.of(pendingRecord));
        when(alipayProvider.queryStatus("ALI_BIZ_002")).thenReturn(SettlementStatus.SUCCESS);
        when(paymentRecordService.getByBatchNo("BATCH_002", null)).thenReturn(List.of(successRecord));

        int scanned = settlementService.reconcileProcessingBatches(5, 50);

        assertEquals(1, scanned);
        verify(paymentRecordService).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
        verify(paymentBatchService).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any());
    }

    @Test
    @DisplayName("主动对账会续提交处理中批次里尚未提交渠道的 pending 记录")
    void reconcileProcessingBatches_shouldResumeUnsubmittedPendingRecord() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(111L);
        batch.setBatchNo("BATCH_PENDING_WITHOUT_PROVIDER_ORDER");
        batch.setPaymentType(PaymentType.BONUS);
        batch.setStatus(BatchStatus.PROCESSING);

        PaymentRecord pendingRecord = new PaymentRecord();
        pendingRecord.setId(11101L);
        pendingRecord.setBatchNo("BATCH_PENDING_WITHOUT_PROVIDER_ORDER");
        pendingRecord.setProviderCode("alipay");
        pendingRecord.setPaymentType(PaymentType.BONUS);
        pendingRecord.setPaymentMethod("ALIPAY");
        pendingRecord.setAmount(new BigDecimal("88.00"));
        pendingRecord.setCurrency("CNY");
        pendingRecord.setRecipientName("赵六");
        pendingRecord.setRecipientAccount("zhaoliu@example.com");
        pendingRecord.setStatus(PaymentStatus.PENDING);

        PaymentRecord successRecord = new PaymentRecord();
        successRecord.setId(11101L);
        successRecord.setBatchNo("BATCH_PENDING_WITHOUT_PROVIDER_ORDER");
        successRecord.setStatus(PaymentStatus.SUCCESS);

        when(paymentBatchService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any()))
                .thenReturn(List.of(batch), List.of());
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any()))
                .thenReturn(List.of(pendingRecord));
        when(paymentRecordService.getById(11101L)).thenReturn(pendingRecord, pendingRecord, pendingRecord);
        when(paymentBatchService.getByBatchNo("BATCH_PENDING_WITHOUT_PROVIDER_ORDER")).thenReturn(batch);
        when(paymentRecordService.update(any())).thenReturn(true);
        when(alipayProvider.singleTransfer(any())).thenReturn(SettlementResult.builder()
                .success(true)
                .providerOrderNo("ALI_RESUMED_11101")
                .providerTradeNo("TRADE_RESUMED_11101")
                .status(SettlementStatus.SUCCESS)
                .build());
        when(paymentRecordService.getByBatchNo("BATCH_PENDING_WITHOUT_PROVIDER_ORDER", null))
                .thenReturn(List.of(successRecord));

        int scanned = settlementService.reconcileProcessingBatches(5, 50);

        assertEquals(1, scanned);
        verify(alipayProvider).singleTransfer(any(SettlementRequest.class));
        verify(alipayProvider, never()).queryStatus(any());
        verify(paymentBatchService).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any());
        verify(notificationService).sendPaymentSuccessNotification(pendingRecord);
    }

    @Test
    @DisplayName("主动对账续提交领取记录失败时不会重复调用渠道")
    void reconcileProcessingBatches_shouldSkipResumeWhenPendingRecordClaimFails() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(112L);
        batch.setBatchNo("BATCH_PENDING_CLAIM_LOST");
        batch.setPaymentType(PaymentType.BONUS);
        batch.setStatus(BatchStatus.PROCESSING);

        PaymentRecord pendingRecord = new PaymentRecord();
        pendingRecord.setId(11201L);
        pendingRecord.setBatchNo("BATCH_PENDING_CLAIM_LOST");
        pendingRecord.setProviderCode("alipay");
        pendingRecord.setPaymentType(PaymentType.BONUS);
        pendingRecord.setStatus(PaymentStatus.PENDING);

        when(paymentBatchService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any()))
                .thenReturn(List.of(batch), List.of());
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any()))
                .thenReturn(List.of(pendingRecord));
        when(paymentRecordService.getById(11201L)).thenReturn(pendingRecord);
        when(paymentBatchService.getByBatchNo("BATCH_PENDING_CLAIM_LOST")).thenReturn(batch);
        when(paymentRecordService.update(any())).thenReturn(false);
        when(paymentRecordService.getByBatchNo("BATCH_PENDING_CLAIM_LOST", null))
                .thenReturn(List.of(pendingRecord));

        int scanned = settlementService.reconcileProcessingBatches(5, 50);

        assertEquals(1, scanned);
        verify(alipayProvider, never()).singleTransfer(any());
        verify(yunzhanghuProvider, never()).singleTransfer(any());
        verify(notificationService, never()).sendPaymentSuccessNotification(any(PaymentRecord.class));
        verify(notificationService, never()).sendPaymentFailedNotification(any(PaymentRecord.class));
    }

    @Test
    @DisplayName("主动对账允许失败记录被渠道最终成功纠正")
    void reconcileProcessingBatches_shouldAllowFailedRecordToConvergeToSuccess() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(12L);
        batch.setBatchNo("BATCH_FAILED_THEN_SUCCESS");
        batch.setStatus(BatchStatus.PROCESSING);

        PaymentRecord failedRecord = new PaymentRecord();
        failedRecord.setId(1201L);
        failedRecord.setBatchNo("BATCH_FAILED_THEN_SUCCESS");
        failedRecord.setProviderCode("yunzhanghu");
        failedRecord.setProviderOrderNo("YZH_LATE_SUCCESS");
        failedRecord.setStatus(PaymentStatus.FAILED);
        failedRecord.setErrorCode("CALLBACK_FAILED");
        failedRecord.setErrorMsg("late failure");

        PaymentRecord successRecord = new PaymentRecord();
        successRecord.setId(1201L);
        successRecord.setBatchNo("BATCH_FAILED_THEN_SUCCESS");
        successRecord.setStatus(PaymentStatus.SUCCESS);

        when(paymentBatchService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any()))
                .thenReturn(List.of(batch), List.of());
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any())).thenReturn(List.of(failedRecord));
        when(yunzhanghuProvider.queryStatus("YZH_LATE_SUCCESS")).thenReturn(SettlementStatus.SUCCESS);
        when(paymentRecordService.getByBatchNo("BATCH_FAILED_THEN_SUCCESS", null)).thenReturn(List.of(successRecord));
        when(paymentRecordService.update(any())).thenReturn(true);

        int scanned = settlementService.reconcileProcessingBatches(5, 50);

        assertEquals(1, scanned);
        assertThat(failedRecord.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(failedRecord.getErrorCode()).isNull();
        assertThat(failedRecord.getErrorMsg()).isNull();
        verify(notificationService).sendPaymentSuccessNotification(failedRecord);
    }

    @Test
    @DisplayName("主动对账不会重复写入仍为失败的历史失败记录")
    void reconcileProcessingBatches_shouldNotRewriteFailedRecordWhenProviderStillFailed() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(13L);
        batch.setBatchNo("BATCH_FAILED_STILL_FAILED");
        batch.setStatus(BatchStatus.PROCESSING);

        PaymentRecord failedRecord = new PaymentRecord();
        failedRecord.setId(1301L);
        failedRecord.setBatchNo("BATCH_FAILED_STILL_FAILED");
        failedRecord.setProviderCode("yunzhanghu");
        failedRecord.setProviderOrderNo("YZH_STILL_FAILED");
        failedRecord.setStatus(PaymentStatus.FAILED);
        failedRecord.setErrorCode("CALLBACK_FAILED");
        failedRecord.setErrorMsg("late failure");

        when(paymentBatchService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any()))
                .thenReturn(List.of(batch), List.of());
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any())).thenReturn(List.of(failedRecord));
        when(yunzhanghuProvider.queryStatus("YZH_STILL_FAILED")).thenReturn(SettlementStatus.FAILED);
        when(paymentRecordService.getByBatchNo("BATCH_FAILED_STILL_FAILED", null)).thenReturn(List.of(failedRecord));

        int scanned = settlementService.reconcileProcessingBatches(5, 50);

        assertEquals(1, scanned);
        assertThat(failedRecord.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRecordService, never()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
        verify(notificationService, never()).sendPaymentFailedNotification(any(PaymentRecord.class));
        verify(notificationService, never()).sendPaymentSuccessNotification(any(PaymentRecord.class));
    }

    @Test
    @DisplayName("主动对账收敛到成功时会发送支付成功通知")
    void reconcileProcessingBatches_shouldNotifyEmployeeWhenPollingReachesSuccess() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(14L);
        batch.setBatchNo("BATCH_NOTIFY_SUCCESS");
        batch.setStatus(BatchStatus.PROCESSING);

        PaymentRecord processingRecord = new PaymentRecord();
        processingRecord.setId(1401L);
        processingRecord.setBatchNo("BATCH_NOTIFY_SUCCESS");
        processingRecord.setProviderCode("yunzhanghu");
        processingRecord.setProviderOrderNo("YZH_NOTIFY_001");
        processingRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PROCESSING);

        PaymentRecord successRecord = new PaymentRecord();
        successRecord.setId(1401L);
        successRecord.setBatchNo("BATCH_NOTIFY_SUCCESS");
        successRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.SUCCESS);

        when(paymentBatchService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any()))
                .thenReturn(List.of(batch), List.of());
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any())).thenReturn(List.of(processingRecord));
        when(yunzhanghuProvider.queryStatus("YZH_NOTIFY_001")).thenReturn(SettlementStatus.SUCCESS);
        when(paymentRecordService.getByBatchNo("BATCH_NOTIFY_SUCCESS", null)).thenReturn(List.of(successRecord));
        when(paymentRecordService.update(any())).thenReturn(true);

        int scanned = settlementService.reconcileProcessingBatches(5, 50);

        assertEquals(1, scanned);
        verify(notificationService).sendPaymentSuccessNotification(processingRecord);
    }

    @Test
    @DisplayName("主动对账会扫描可恢复的失败批次，纠正渠道晚成功结果")
    void reconcileProcessingBatches_shouldRecoverFailedTerminalBatchWhenProviderEventuallySuccess() {
        PaymentBatch failedBatch = new PaymentBatch();
        failedBatch.setId(15L);
        failedBatch.setBatchNo("BATCH_TERMINAL_FAILED_THEN_SUCCESS");
        failedBatch.setStatus(BatchStatus.FAILED);
        failedBatch.setPaymentStatus(com.yiyundao.compensation.enums.PaymentBatchProcessStatus.FAILED);

        PaymentRecord failedRecord = new PaymentRecord();
        failedRecord.setId(1501L);
        failedRecord.setBatchNo("BATCH_TERMINAL_FAILED_THEN_SUCCESS");
        failedRecord.setProviderCode("yunzhanghu");
        failedRecord.setProviderOrderNo("YZH_TERMINAL_LATE_SUCCESS");
        failedRecord.setStatus(PaymentStatus.FAILED);
        failedRecord.setErrorCode("SUBMIT_TIMEOUT");
        failedRecord.setErrorMsg("submit timeout");

        PaymentRecord successRecord = new PaymentRecord();
        successRecord.setId(1501L);
        successRecord.setBatchNo("BATCH_TERMINAL_FAILED_THEN_SUCCESS");
        successRecord.setStatus(PaymentStatus.SUCCESS);

        when(paymentBatchService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any()))
                .thenReturn(List.of(), List.of(failedBatch));
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any()))
                .thenReturn(List.of(failedRecord));
        when(yunzhanghuProvider.queryStatus("YZH_TERMINAL_LATE_SUCCESS")).thenReturn(SettlementStatus.SUCCESS);
        when(paymentRecordService.getByBatchNo("BATCH_TERMINAL_FAILED_THEN_SUCCESS", null))
                .thenReturn(List.of(successRecord));
        when(paymentRecordService.update(any())).thenReturn(true);

        int scanned = settlementService.reconcileProcessingBatches(5, 50);

        assertEquals(1, scanned);
        assertThat(failedRecord.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentRecordService, org.mockito.Mockito.times(2))
                .update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
        verify(paymentBatchService).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any());
        verify(notificationService).sendPaymentSuccessNotification(failedRecord);
    }

    @Test
    @DisplayName("主动对账不会把终态失败批次回退为处理中")
    void reconcileProcessingBatches_shouldNotReopenTerminalFailedBatchWhenRecordsRemainProcessing() {
        PaymentBatch failedBatch = new PaymentBatch();
        failedBatch.setId(16L);
        failedBatch.setBatchNo("BATCH_TERMINAL_FAILED_STILL_PROCESSING");
        failedBatch.setStatus(BatchStatus.FAILED);
        failedBatch.setPaymentStatus(com.yiyundao.compensation.enums.PaymentBatchProcessStatus.FAILED);

        PaymentRecord failedRecord = new PaymentRecord();
        failedRecord.setId(1601L);
        failedRecord.setBatchNo("BATCH_TERMINAL_FAILED_STILL_PROCESSING");
        failedRecord.setProviderCode("yunzhanghu");
        failedRecord.setProviderOrderNo("YZH_TERMINAL_STILL_PROCESSING");
        failedRecord.setStatus(PaymentStatus.FAILED);

        PaymentRecord processingRecord = new PaymentRecord();
        processingRecord.setId(1601L);
        processingRecord.setBatchNo("BATCH_TERMINAL_FAILED_STILL_PROCESSING");
        processingRecord.setStatus(PaymentStatus.PROCESSING);

        when(paymentBatchService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any()))
                .thenReturn(List.of(), List.of(failedBatch));
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any()))
                .thenReturn(List.of(failedRecord));
        when(yunzhanghuProvider.queryStatus("YZH_TERMINAL_STILL_PROCESSING"))
                .thenReturn(SettlementStatus.PROCESSING);
        when(paymentRecordService.getByBatchNo("BATCH_TERMINAL_FAILED_STILL_PROCESSING", null))
                .thenReturn(List.of(processingRecord));

        int scanned = settlementService.reconcileProcessingBatches(5, 50);

        assertEquals(1, scanned);
        verify(paymentBatchService, never()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any());
        verify(notificationService, never()).sendBatchCompleteNotification(any(PaymentBatch.class));
    }

    @Test
    @DisplayName("主动对账会限制错误配置导致的过大扫描范围")
    void reconcileProcessingBatches_shouldClampConfiguredLimits() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(19L);
        batch.setBatchNo("BATCH_RECONCILE_LIMIT");
        batch.setStatus(BatchStatus.PROCESSING);

        when(paymentBatchService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any()))
                .thenReturn(List.of(batch), List.of());
        when(paymentRecordService.list(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any())).thenReturn(List.of());
        when(paymentRecordService.getByBatchNo("BATCH_RECONCILE_LIMIT", null)).thenReturn(List.of());

        int scanned = settlementService.reconcileProcessingBatches(10_000, 10_000);

        assertEquals(1, scanned);
        ArgumentCaptor<Wrapper<PaymentBatch>> batchWrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        ArgumentCaptor<Wrapper<PaymentRecord>> recordWrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(paymentBatchService, org.mockito.Mockito.times(2)).list(batchWrapperCaptor.capture());
        verify(paymentRecordService).list(recordWrapperCaptor.capture());
        assertThat(batchWrapperCaptor.getAllValues())
                .extracting(this::lastSql)
                .containsExactly(" limit 100", " limit 99");
        assertThat(lastSql(recordWrapperCaptor.getValue())).contains("limit 500");
    }

    private String lastSql(Wrapper<?> wrapper) {
        Object value = readField(wrapper, "lastSql");
        return value instanceof SharedString sharedString ? sharedString.getStringValue() : null;
    }

    private Object readField(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new AssertionError("读取 wrapper 字段失败: " + fieldName, e);
            }
        }
        throw new AssertionError("未找到 wrapper 字段: " + fieldName);
    }

    @Test
    @DisplayName("批量提交失败时会发送支付失败通知")
    void batchTransferShouldSendFailureNotificationWhenProviderReturnsFailedResult() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(12L);
        batch.setBatchNo("BATCH_FAIL_NOTIFY_1");
        batch.setStatus(BatchStatus.SUBMITTED);

        PaymentRecord pendingRecord = new PaymentRecord();
        pendingRecord.setId(1201L);
        pendingRecord.setBatchNo("BATCH_FAIL_NOTIFY_1");
        pendingRecord.setProviderCode("alipay");
        pendingRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PENDING);

        PaymentRecord failedRecord = new PaymentRecord();
        failedRecord.setId(1201L);
        failedRecord.setBatchNo("BATCH_FAIL_NOTIFY_1");
        failedRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.FAILED);

        when(paymentBatchService.getByBatchNo("BATCH_FAIL_NOTIFY_1")).thenReturn(batch);
        when(paymentBatchService.update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any())).thenReturn(true);
        when(paymentRecordService.getByBatchNo("BATCH_FAIL_NOTIFY_1", com.yiyundao.compensation.enums.PaymentStatus.PENDING))
                .thenReturn(List.of(pendingRecord));
        when(paymentRecordService.getById(1201L)).thenReturn(pendingRecord, pendingRecord);
        when(alipayProvider.singleTransfer(any())).thenReturn(SettlementResult.builder()
                .success(false)
                .status(SettlementStatus.FAILED)
                .errorCode("CHANNEL_REJECTED")
                .errorMsg("channel rejected")
                .build());
        when(paymentRecordService.update(any())).thenReturn(true);
        when(paymentRecordService.getByBatchNo("BATCH_FAIL_NOTIFY_1", null)).thenReturn(List.of(failedRecord));
        when(payrollPaymentFailureServiceProvider.getIfAvailable()).thenReturn(payrollPaymentFailureService);

        settlementService.batchTransfer("BATCH_FAIL_NOTIFY_1");

        verify(notificationService).sendPaymentFailedNotification(any(PaymentRecord.class));
        verify(payrollPaymentFailureService).markUnresolvedByPaymentBatchNo(
                "BATCH_FAIL_NOTIFY_1", "支付批次支付失败");
    }

    @Test
    @DisplayName("完整成功后才会将支付补偿记录标记为已解决")
    void batchTransferShouldResolveCompensationOnlyAfterFullSuccess() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(14L);
        batch.setBatchNo("BATCH_SUCCESS_COMPENSATION");
        batch.setStatus(BatchStatus.SUBMITTED);

        PaymentRecord pendingRecord = salaryRecord(1401L, "BATCH_SUCCESS_COMPENSATION", PaymentStatus.PENDING);
        pendingRecord.setProviderCode("alipay");
        PaymentRecord successRecord = salaryRecord(1401L, "BATCH_SUCCESS_COMPENSATION", PaymentStatus.SUCCESS);
        successRecord.setProviderCode("alipay");

        when(paymentBatchService.getByBatchNo("BATCH_SUCCESS_COMPENSATION")).thenReturn(batch);
        when(paymentBatchService.update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any())).thenReturn(true);
        when(paymentRecordService.getByBatchNo("BATCH_SUCCESS_COMPENSATION", PaymentStatus.PENDING))
                .thenReturn(List.of(pendingRecord));
        when(paymentRecordService.getById(1401L)).thenReturn(pendingRecord, pendingRecord);
        when(alipayProvider.singleTransfer(any())).thenReturn(SettlementResult.builder()
                .success(true)
                .status(SettlementStatus.SUCCESS)
                .providerOrderNo("ALI_SUCCESS_1401")
                .providerTradeNo("ALI_TRADE_1401")
                .build());
        when(paymentRecordService.update(any())).thenReturn(true);
        when(paymentRecordService.getByBatchNo("BATCH_SUCCESS_COMPENSATION", null))
                .thenReturn(List.of(successRecord));
        when(payrollPaymentFailureServiceProvider.getIfAvailable()).thenReturn(payrollPaymentFailureService);

        settlementService.batchTransfer("BATCH_SUCCESS_COMPENSATION");

        verify(payrollPaymentFailureService).markResolvedByPaymentBatchNo("BATCH_SUCCESS_COMPENSATION");
    }

    @Test
    @DisplayName("批量转账遇到处理中批次时不会重复提交渠道")
    void batchTransferShouldSkipAlreadyProcessingBatch() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(16L);
        batch.setBatchNo("BATCH_ALREADY_PROCESSING");
        batch.setStatus(BatchStatus.PROCESSING);

        when(paymentBatchService.getByBatchNo("BATCH_ALREADY_PROCESSING")).thenReturn(batch);

        settlementService.batchTransfer("BATCH_ALREADY_PROCESSING");

        verify(paymentBatchService, never()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any());
        verify(paymentRecordService, never()).getByBatchNo("BATCH_ALREADY_PROCESSING",
                com.yiyundao.compensation.enums.PaymentStatus.PENDING);
        verify(alipayProvider, never()).singleTransfer(any());
        verify(yunzhanghuProvider, never()).singleTransfer(any());
    }

    @Test
    @DisplayName("批量转账领取批次失败时不会重复提交渠道")
    void batchTransferShouldSkipWhenBatchClaimFails() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(17L);
        batch.setBatchNo("BATCH_CLAIM_LOST");
        batch.setStatus(BatchStatus.SUBMITTED);

        when(paymentBatchService.getByBatchNo("BATCH_CLAIM_LOST")).thenReturn(batch);
        when(paymentBatchService.update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any())).thenReturn(false);

        settlementService.batchTransfer("BATCH_CLAIM_LOST");

        verify(paymentBatchService).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any());
        verify(paymentRecordService, never()).getByBatchNo("BATCH_CLAIM_LOST",
                com.yiyundao.compensation.enums.PaymentStatus.PENDING);
        verify(alipayProvider, never()).singleTransfer(any());
        verify(yunzhanghuProvider, never()).singleTransfer(any());
    }

    @Test
    @DisplayName("过期发放单关联的工资支付批次不会启动渠道转账")
    void batchTransferShouldBlockStalePayrollDistributionRevision() {
        PaymentBatch batch = payrollPaymentBatch("BATCH_STALE_DISTRIBUTION", 1700L);
        PayrollDistributionService distributionService = org.mockito.Mockito.mock(PayrollDistributionService.class);
        PayrollDistribution distribution = payrollDistribution(1700L, 17L, 1);
        PayrollBatch payrollBatch = payrollBatch("BATCH_STALE_DISTRIBUTION", 2, PayrollBatchStatus.PAY_PROCESSING);

        when(paymentBatchService.getByBatchNo("BATCH_STALE_DISTRIBUTION")).thenReturn(batch);
        when(payrollDistributionServiceProvider.getIfAvailable()).thenReturn(distributionService);
        when(distributionService.getById(1700L)).thenReturn(distribution);
        when(payrollBatchMapper.selectById(17L)).thenReturn(payrollBatch);

        settlementService.batchTransfer("BATCH_STALE_DISTRIBUTION");

        verify(paymentBatchService).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any());
        verify(paymentRecordService, never()).getByBatchNo("BATCH_STALE_DISTRIBUTION", PaymentStatus.PENDING);
        verify(alipayProvider, never()).singleTransfer(any());
        verify(yunzhanghuProvider, never()).singleTransfer(any());
    }

    @Test
    @DisplayName("批量转账领取单条记录失败时不误标失败")
    void batchTransferShouldNotMarkRecordFailedWhenRecordClaimFails() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(18L);
        batch.setBatchNo("BATCH_RECORD_CLAIM_LOST");
        batch.setStatus(BatchStatus.SUBMITTED);

        PaymentRecord pendingRecord = new PaymentRecord();
        pendingRecord.setId(1801L);
        pendingRecord.setBatchNo("BATCH_RECORD_CLAIM_LOST");
        pendingRecord.setProviderCode("alipay");
        pendingRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PENDING);

        when(paymentBatchService.getByBatchNo("BATCH_RECORD_CLAIM_LOST")).thenReturn(batch);
        when(paymentBatchService.update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any())).thenReturn(true);
        when(paymentRecordService.getByBatchNo("BATCH_RECORD_CLAIM_LOST", com.yiyundao.compensation.enums.PaymentStatus.PENDING))
                .thenReturn(List.of(pendingRecord));
        when(paymentRecordService.getById(1801L)).thenReturn(pendingRecord);
        when(paymentRecordService.update(any())).thenReturn(false);
        when(paymentRecordService.getByBatchNo("BATCH_RECORD_CLAIM_LOST", null)).thenReturn(List.of(pendingRecord));

        settlementService.batchTransfer("BATCH_RECORD_CLAIM_LOST");

        verify(alipayProvider, never()).singleTransfer(any());
        verify(notificationService, never()).sendPaymentFailedNotification(any(PaymentRecord.class));
    }

    @Test
    @DisplayName("渠道已落库失败时二次失败更新未命中仍会发送失败通知")
    void batchTransferShouldNotifyFailureWhenProviderAlreadyPersistedFailedRecord() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(15L);
        batch.setBatchNo("BATCH_FAIL_NOTIFY_3");
        batch.setStatus(BatchStatus.SUBMITTED);

        PaymentRecord pendingRecord = new PaymentRecord();
        pendingRecord.setId(1501L);
        pendingRecord.setBatchNo("BATCH_FAIL_NOTIFY_3");
        pendingRecord.setProviderCode("alipay");
        pendingRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PENDING);

        PaymentRecord failedRecord = new PaymentRecord();
        failedRecord.setId(1501L);
        failedRecord.setBatchNo("BATCH_FAIL_NOTIFY_3");
        failedRecord.setProviderCode("alipay");
        failedRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.FAILED);
        failedRecord.setErrorCode("CHANNEL_REJECTED");
        failedRecord.setErrorMsg("channel rejected");

        when(paymentBatchService.getByBatchNo("BATCH_FAIL_NOTIFY_3")).thenReturn(batch);
        when(paymentBatchService.update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any())).thenReturn(true);
        when(paymentRecordService.getByBatchNo("BATCH_FAIL_NOTIFY_3", com.yiyundao.compensation.enums.PaymentStatus.PENDING))
                .thenReturn(List.of(pendingRecord));
        when(paymentRecordService.getById(1501L))
                .thenReturn(pendingRecord, pendingRecord, pendingRecord, pendingRecord, failedRecord);
        when(alipayProvider.singleTransfer(any())).thenReturn(SettlementResult.builder()
                .success(false)
                .status(SettlementStatus.FAILED)
                .errorCode("CHANNEL_REJECTED")
                .errorMsg("channel rejected")
                .build());
        when(paymentRecordService.update(any())).thenReturn(true, false, false, true);
        when(paymentRecordService.getByBatchNo("BATCH_FAIL_NOTIFY_3", null)).thenReturn(List.of(failedRecord));

        settlementService.batchTransfer("BATCH_FAIL_NOTIFY_3");

        verify(notificationService).sendPaymentFailedNotification(failedRecord);
    }

    @Test
    @DisplayName("批量提交异常时会发送支付失败通知")
    void batchTransferShouldSendFailureNotificationWhenProviderThrowsException() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(13L);
        batch.setBatchNo("BATCH_FAIL_NOTIFY_2");
        batch.setStatus(BatchStatus.SUBMITTED);

        PaymentRecord pendingRecord = new PaymentRecord();
        pendingRecord.setId(1301L);
        pendingRecord.setBatchNo("BATCH_FAIL_NOTIFY_2");
        pendingRecord.setProviderCode("alipay");
        pendingRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.PENDING);

        PaymentRecord failedRecord = new PaymentRecord();
        failedRecord.setId(1301L);
        failedRecord.setBatchNo("BATCH_FAIL_NOTIFY_2");
        failedRecord.setStatus(com.yiyundao.compensation.enums.PaymentStatus.FAILED);

        when(paymentBatchService.getByBatchNo("BATCH_FAIL_NOTIFY_2")).thenReturn(batch);
        when(paymentBatchService.update(org.mockito.ArgumentMatchers.<Wrapper<PaymentBatch>>any())).thenReturn(true);
        when(paymentRecordService.getByBatchNo("BATCH_FAIL_NOTIFY_2", com.yiyundao.compensation.enums.PaymentStatus.PENDING))
                .thenReturn(List.of(pendingRecord));
        when(paymentRecordService.getById(1301L)).thenReturn(pendingRecord, pendingRecord);
        when(alipayProvider.singleTransfer(any())).thenThrow(new IllegalStateException("provider down"));
        when(paymentRecordService.update(any())).thenReturn(true);
        when(paymentRecordService.getByBatchNo("BATCH_FAIL_NOTIFY_2", null)).thenReturn(List.of(failedRecord));

        settlementService.batchTransfer("BATCH_FAIL_NOTIFY_2");

        verify(notificationService).sendPaymentFailedNotification(any(PaymentRecord.class));
    }
}
