package com.yiyundao.compensation.modules.payment.provider.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.provider.SettlementCallbackResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementRequest;
import com.yiyundao.compensation.modules.payment.provider.SettlementResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementStatus;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yiyundao.compensation.service.EncryptionService;
import com.yiyundao.compensation.service.YunzhanghuClient;
import com.yunzhanghu.sdk.YzhException;
import com.yunzhanghu.sdk.base.YzhResponse;
import com.yunzhanghu.sdk.notify.domain.NotifyResponse;
import com.yunzhanghu.sdk.payment.domain.CreateAlipayOrderResponse;
import com.yunzhanghu.sdk.payment.domain.GetOrderResponse;
import com.yunzhanghu.sdk.payment.domain.NotifyOrderData;
import com.yunzhanghu.sdk.payment.domain.NotifyOrderRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("YunzhanghuSettlementProvider 测试")
class YunzhanghuSettlementProviderTest {

    @Mock
    private IntegrationConfigService integrationConfigService;

    @Mock
    private YunzhanghuClient yunzhanghuClient;

    @Mock
    private PaymentRecordService paymentRecordService;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private ObjectProvider<EmployeeService> employeeServiceProvider;

    @Mock
    private EncryptionService encryptionService;

    private YunzhanghuSettlementProvider provider;

    @BeforeEach
    void setUp() {
        provider = new YunzhanghuSettlementProvider(
                integrationConfigService,
                yunzhanghuClient,
                paymentRecordService,
                employeeServiceProvider,
                encryptionService,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("单笔发放成功：调用SDK并写入provider字段")
    void singleTransfer_shouldSuccess() throws Exception {
        SettlementRequest request = SettlementRequest.builder()
                .paymentRecordId(1001L)
                .bizNo("YZH_ORDER_1001")
                .amount(new BigDecimal("88.66"))
                .recipientName("张三")
                .recipientAccount("zhangsan@alipay")
                .remark("工资发放")
                .build();

        PaymentRecord record = new PaymentRecord();
        record.setId(1001L);
        record.setEmployeeId(9001L);
        when(paymentRecordService.getById(1001L)).thenReturn(record);
        when(employeeServiceProvider.getIfAvailable()).thenReturn(employeeService);

        Employee employee = new Employee();
        employee.setId(9001L);
        employee.setEncryptedIdCard("enc-id");
        employee.setPhone("13800138000");
        when(employeeService.getById(9001L)).thenReturn(employee);
        when(encryptionService.decryptIdCard("enc-id")).thenReturn("110101199001011234");

        CreateAlipayOrderResponse data = new CreateAlipayOrderResponse();
        data.setOrderId("YZH_ORDER_1001");
        data.setRef("YZH_REF_001");
        data.setPay("88.66");
        YzhResponse<CreateAlipayOrderResponse> response = new YzhResponse<>();
        response.setHttpCode(200);
        response.setCode("0000");
        response.setMessage("OK");
        response.setRequestId("req-001");
        response.setData(data);
        when(yunzhanghuClient.createAlipayOrder(
                eq("YZH_ORDER_1001"),
                eq(new BigDecimal("88.66")),
                eq("张三"),
                eq("zhangsan@alipay"),
                eq("110101199001011234"),
                eq("13800138000"),
                eq("工资发放"),
                eq("9001"),
                eq("张三")
        )).thenReturn(response);

        SettlementResult result = provider.singleTransfer(request);

        assertTrue(result.isSuccess());
        assertEquals(SettlementStatus.PROCESSING, result.getStatus());
        assertEquals("YZH_ORDER_1001", result.getProviderOrderNo());
        assertEquals("YZH_REF_001", result.getProviderTradeNo());

        verify(paymentRecordService).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
    }

    @Test
    @DisplayName("重复下单响应：沿用原订单号查单，不直接标记失败")
    void singleTransfer_shouldQueryOriginalOrderWhenDuplicateResponse() throws Exception {
        SettlementRequest request = SettlementRequest.builder()
                .paymentRecordId(1004L)
                .bizNo("YZH_ORDER_1004")
                .amount(new BigDecimal("88.66"))
                .recipientName("张三")
                .recipientAccount("zhangsan@alipay")
                .build();

        PaymentRecord record = new PaymentRecord();
        record.setId(1004L);
        record.setEmployeeId(9004L);
        when(paymentRecordService.getById(1004L)).thenReturn(record);
        when(employeeServiceProvider.getIfAvailable()).thenReturn(employeeService);

        Employee employee = new Employee();
        employee.setId(9004L);
        employee.setEncryptedIdCard("enc-id-4");
        employee.setPhone("13800138004");
        when(employeeService.getById(9004L)).thenReturn(employee);
        when(encryptionService.decryptIdCard("enc-id-4")).thenReturn("110101199001011234");

        YzhResponse<CreateAlipayOrderResponse> duplicate = new YzhResponse<>();
        duplicate.setHttpCode(200);
        duplicate.setCode("2002");
        duplicate.setMessage("已上传过该笔流水");
        when(yunzhanghuClient.createAlipayOrder(
                eq("YZH_ORDER_1004"),
                eq(new BigDecimal("88.66")),
                eq("张三"),
                eq("zhangsan@alipay"),
                eq("110101199001011234"),
                eq("13800138004"),
                eq(null),
                eq("9004"),
                eq("张三")
        )).thenReturn(duplicate);

        GetOrderResponse order = new GetOrderResponse();
        order.setOrderId("YZH_ORDER_1004");
        order.setRef("YZH_REF_004");
        order.setStatus("1");
        order.setStatusDetail("0");
        YzhResponse<GetOrderResponse> query = new YzhResponse<>();
        query.setHttpCode(200);
        query.setCode("0000");
        query.setData(order);
        when(yunzhanghuClient.queryOrder("YZH_ORDER_1004")).thenReturn(query);

        SettlementResult result = provider.singleTransfer(request);

        assertTrue(result.isSuccess());
        assertEquals(SettlementStatus.SUCCESS, result.getStatus());
        assertEquals("YZH_ORDER_1004", result.getProviderOrderNo());
        assertEquals("YZH_REF_004", result.getProviderTradeNo());
        verify(yunzhanghuClient).queryOrder("YZH_ORDER_1004");
    }

    @Test
    @DisplayName("网络异常：原订单状态未知时保持处理中")
    void singleTransfer_shouldKeepProcessingWhenSubmitResultUnknown() throws Exception {
        SettlementRequest request = SettlementRequest.builder()
                .paymentRecordId(1005L)
                .bizNo("YZH_ORDER_1005")
                .amount(new BigDecimal("88.66"))
                .recipientName("张三")
                .recipientAccount("zhangsan@alipay")
                .build();

        PaymentRecord record = new PaymentRecord();
        record.setId(1005L);
        record.setEmployeeId(9005L);
        when(paymentRecordService.getById(1005L)).thenReturn(record);
        when(employeeServiceProvider.getIfAvailable()).thenReturn(employeeService);
        Employee employee = new Employee();
        employee.setId(9005L);
        employee.setEncryptedIdCard("enc-id-5");
        employee.setPhone("13800138005");
        when(employeeService.getById(9005L)).thenReturn(employee);
        when(encryptionService.decryptIdCard("enc-id-5")).thenReturn("110101199001011234");

        when(yunzhanghuClient.createAlipayOrder(
                eq("YZH_ORDER_1005"), any(), eq("张三"), eq("zhangsan@alipay"),
                eq("110101199001011234"), eq("13800138005"), eq(null), eq("9005"), eq("张三")
        )).thenThrow(new YzhException("timeout"));
        when(yunzhanghuClient.queryOrder("YZH_ORDER_1005")).thenReturn(null);

        SettlementResult result = provider.singleTransfer(request);

        assertTrue(result.isSuccess());
        assertEquals(SettlementStatus.PROCESSING, result.getStatus());
        assertEquals("YZH_ORDER_1005", result.getProviderOrderNo());
        assertEquals("YZH_ORDER_UNKNOWN", result.getErrorCode());
        verify(yunzhanghuClient).queryOrder("YZH_ORDER_1005");
    }

    @Test
    @DisplayName("单笔发放失败：缺少身份证信息")
    void singleTransfer_shouldFailWhenIdCardMissing() {
        SettlementRequest request = SettlementRequest.builder()
                .paymentRecordId(1002L)
                .bizNo("YZH_LOCAL_VALIDATION_1002")
                .amount(new BigDecimal("99.00"))
                .recipientName("李四")
                .recipientAccount("lisi@alipay")
                .build();

        PaymentRecord record = new PaymentRecord();
        record.setId(1002L);
        record.setEmployeeId(9002L);
        when(paymentRecordService.getById(1002L)).thenReturn(record);
        when(employeeServiceProvider.getIfAvailable()).thenReturn(employeeService);

        Employee employee = new Employee();
        employee.setId(9002L);
        employee.setEncryptedIdCard("enc-id-2");
        when(employeeService.getById(9002L)).thenReturn(employee);
        when(encryptionService.decryptIdCard("enc-id-2")).thenReturn(null);

        SettlementResult result = provider.singleTransfer(request);

        assertFalse(result.isSuccess());
        assertEquals("YZH_ID_CARD_MISSING", result.getErrorCode());
        assertEquals(SettlementStatus.FAILED, result.getStatus());
        ArgumentCaptor<Wrapper<PaymentRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(paymentRecordService).update(captor.capture());
        assertFalse(readUpdateValues(captor.getValue()).containsValue("YZH_LOCAL_VALIDATION_1002"));
    }

    @Test
    @DisplayName("查单状态映射：审核中")
    void queryStatus_shouldMapAuditing() throws Exception {
        GetOrderResponse data = new GetOrderResponse();
        data.setStatus("AUDITING");
        data.setStatusDetail("RISK_AUDITING");
        YzhResponse<GetOrderResponse> response = new YzhResponse<>();
        response.setHttpCode(200);
        response.setCode("0000");
        response.setData(data);
        when(yunzhanghuClient.queryOrder("YZH_ORDER_1003")).thenReturn(response);

        SettlementStatus status = provider.queryStatus("YZH_ORDER_1003");

        assertEquals(SettlementStatus.AUDITING, status);
    }

    @Test
    @DisplayName("查单状态映射：云账户数字终态")
    void queryStatus_shouldMapNumericStatuses() throws Exception {
        GetOrderResponse successData = new GetOrderResponse();
        successData.setStatus("1");
        successData.setStatusDetail("0");
        YzhResponse<GetOrderResponse> successResponse = new YzhResponse<>();
        successResponse.setHttpCode(200);
        successResponse.setCode("0000");
        successResponse.setData(successData);
        when(yunzhanghuClient.queryOrder("YZH_ORDER_SUCCESS")).thenReturn(successResponse);

        GetOrderResponse failedData = new GetOrderResponse();
        failedData.setStatus("2");
        failedData.setStatusDetail("252");
        YzhResponse<GetOrderResponse> failedResponse = new YzhResponse<>();
        failedResponse.setHttpCode(200);
        failedResponse.setCode("0000");
        failedResponse.setData(failedData);
        when(yunzhanghuClient.queryOrder("YZH_ORDER_FAILED")).thenReturn(failedResponse);

        GetOrderResponse cancelledData = new GetOrderResponse();
        cancelledData.setStatus("15");
        cancelledData.setStatusDetail("0");
        YzhResponse<GetOrderResponse> cancelledResponse = new YzhResponse<>();
        cancelledResponse.setHttpCode(200);
        cancelledResponse.setCode("0000");
        cancelledResponse.setData(cancelledData);
        when(yunzhanghuClient.queryOrder("YZH_ORDER_CANCELLED")).thenReturn(cancelledResponse);

        assertEquals(SettlementStatus.SUCCESS, provider.queryStatus("YZH_ORDER_SUCCESS"));
        assertEquals(SettlementStatus.FAILED, provider.queryStatus("YZH_ORDER_FAILED"));
        assertEquals(SettlementStatus.CANCELLED, provider.queryStatus("YZH_ORDER_CANCELLED"));
    }

    @Test
    @DisplayName("查单状态映射：未知数字主状态不被状态详情文本覆盖")
    void queryStatus_shouldKeepUnknownNumericStatusProcessing() throws Exception {
        GetOrderResponse data = new GetOrderResponse();
        data.setStatus("99");
        data.setStatusDetail("SUCCESS");
        YzhResponse<GetOrderResponse> response = new YzhResponse<>();
        response.setHttpCode(200);
        response.setCode("0000");
        response.setData(data);
        when(yunzhanghuClient.queryOrder("YZH_ORDER_UNKNOWN_STATUS")).thenReturn(response);

        assertEquals(SettlementStatus.PROCESSING, provider.queryStatus("YZH_ORDER_UNKNOWN_STATUS"));
    }

    @Test
    @DisplayName("回调处理：验签通过后更新支付记录")
    void callback_shouldUpdateRecordWhenVerified() {
        NotifyOrderData notifyData = new NotifyOrderData();
        notifyData.setOrderId("YZH_ORDER_CB_1");
        notifyData.setRef("YZH_REF_CB_1");
        notifyData.setStatus("1");
        notifyData.setStatusDetail("0");

        NotifyOrderRequest notifyOrderRequest = new NotifyOrderRequest();
        notifyOrderRequest.setNotifyId("notify-1");
        notifyOrderRequest.setNotifyTime("2026-02-26 10:00:00");
        notifyOrderRequest.setData(notifyData);

        NotifyResponse<NotifyOrderRequest> decoded = new NotifyResponse<>();
        decoded.setSignRes(true);
        decoded.setDescryptRes(true);
        decoded.setData(notifyOrderRequest);
        when(yunzhanghuClient.decodeOrderNotify(any())).thenReturn(decoded);

        PaymentRecord record = new PaymentRecord();
        record.setId(2001L);
        record.setStatus(PaymentStatus.PROCESSING);
        when(paymentRecordService.getByProviderOrderNo("yunzhanghu", "YZH_ORDER_CB_1")).thenReturn(record);
        when(paymentRecordService.update(any(Wrapper.class))).thenReturn(true);

        boolean verified = provider.verifyCallback(Map.of("data", "x", "mess", "m", "timestamp", "t", "sign", "s"));
        SettlementCallbackResult callbackResult = provider.handleCallback(Map.of());

        assertTrue(verified);
        assertTrue(callbackResult.isSuccess());
        assertEquals("YZH_ORDER_CB_1", callbackResult.getBizNo());
        assertEquals(SettlementStatus.SUCCESS, callbackResult.getStatus());

        verify(paymentRecordService).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
    }

    @Test
    @DisplayName("回调处理：已成功记录不被迟到失败回调降级")
    void callback_shouldNotDowngradeSuccessfulRecord() {
        NotifyOrderData notifyData = new NotifyOrderData();
        notifyData.setOrderId("YZH_ORDER_CB_DONE");
        notifyData.setRef("YZH_REF_CB_DONE");
        notifyData.setStatus("FAILED");
        notifyData.setStatusDetail("PAY_FAILED");

        NotifyOrderRequest notifyOrderRequest = new NotifyOrderRequest();
        notifyOrderRequest.setNotifyId("notify-done");
        notifyOrderRequest.setNotifyTime("2026-02-26 10:00:00");
        notifyOrderRequest.setData(notifyData);

        NotifyResponse<NotifyOrderRequest> decoded = new NotifyResponse<>();
        decoded.setSignRes(true);
        decoded.setDescryptRes(true);
        decoded.setData(notifyOrderRequest);
        when(yunzhanghuClient.decodeOrderNotify(any())).thenReturn(decoded);

        PaymentRecord record = new PaymentRecord();
        record.setId(2002L);
        record.setStatus(PaymentStatus.SUCCESS);
        when(paymentRecordService.getByProviderOrderNo("yunzhanghu", "YZH_ORDER_CB_DONE")).thenReturn(record);

        assertTrue(provider.verifyCallback(Map.of("data", "x", "mess", "m", "timestamp", "t", "sign", "s")));
        SettlementCallbackResult callbackResult = provider.handleCallback(Map.of());

        assertTrue(callbackResult.isSuccess());
        assertEquals(SettlementStatus.FAILED, callbackResult.getStatus());
        verify(paymentRecordService, never()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
    }

    @Test
    @DisplayName("重复成功回调：不重置既有支付时间")
    void callback_shouldNotResetPaymentTimeForDuplicateSuccess() {
        NotifyOrderData notifyData = new NotifyOrderData();
        notifyData.setOrderId("YZH_ORDER_CB_DUPLICATE");
        notifyData.setRef("YZH_REF_CB_DUPLICATE");
        notifyData.setStatus("1");
        notifyData.setStatusDetail("0");

        NotifyOrderRequest notifyOrderRequest = new NotifyOrderRequest();
        notifyOrderRequest.setNotifyId("notify-duplicate");
        notifyOrderRequest.setData(notifyData);

        NotifyResponse<NotifyOrderRequest> decoded = new NotifyResponse<>();
        decoded.setSignRes(true);
        decoded.setDescryptRes(true);
        decoded.setData(notifyOrderRequest);
        when(yunzhanghuClient.decodeOrderNotify(any())).thenReturn(decoded);

        PaymentRecord record = new PaymentRecord();
        record.setId(2003L);
        record.setStatus(PaymentStatus.SUCCESS);
        LocalDateTime paymentTime = LocalDateTime.of(2026, 2, 26, 10, 0);
        record.setPaymentTime(paymentTime);
        when(paymentRecordService.getByProviderOrderNo("yunzhanghu", "YZH_ORDER_CB_DUPLICATE"))
                .thenReturn(record);
        when(paymentRecordService.update(any(Wrapper.class))).thenReturn(true);

        assertTrue(provider.verifyCallback(Map.of("data", "x", "mess", "m", "timestamp", "t", "sign", "s")));
        SettlementCallbackResult callbackResult = provider.handleCallback(Map.of());

        assertTrue(callbackResult.isSuccess());
        ArgumentCaptor<Wrapper<PaymentRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(paymentRecordService).update(captor.capture());
        assertFalse(readUpdateValues(captor.getValue()).containsValue(paymentTime));
    }

    @Test
    @DisplayName("回调处理：验签通过但未匹配支付记录时不确认成功")
    void callback_shouldNotAckWhenPaymentRecordMissing() {
        NotifyOrderData notifyData = new NotifyOrderData();
        notifyData.setOrderId("YZH_ORDER_MISSING");
        notifyData.setRef("YZH_REF_MISSING");
        notifyData.setStatus("1");
        notifyData.setStatusDetail("0");

        NotifyOrderRequest notifyOrderRequest = new NotifyOrderRequest();
        notifyOrderRequest.setNotifyId("notify-missing");
        notifyOrderRequest.setNotifyTime("2026-02-26 10:00:00");
        notifyOrderRequest.setData(notifyData);

        NotifyResponse<NotifyOrderRequest> decoded = new NotifyResponse<>();
        decoded.setSignRes(true);
        decoded.setDescryptRes(true);
        decoded.setData(notifyOrderRequest);
        when(yunzhanghuClient.decodeOrderNotify(any())).thenReturn(decoded);
        when(paymentRecordService.getByProviderOrderNo("yunzhanghu", "YZH_ORDER_MISSING")).thenReturn(null);

        assertTrue(provider.verifyCallback(Map.of("data", "x", "mess", "m", "timestamp", "t", "sign", "s")));
        SettlementCallbackResult callbackResult = provider.handleCallback(Map.of());

        assertFalse(callbackResult.isSuccess());
        assertEquals("YZH_ORDER_MISSING", callbackResult.getBizNo());
        assertEquals(SettlementStatus.SUCCESS, callbackResult.getStatus());
        assertEquals("云账户回调未匹配到支付记录", callbackResult.getErrorMsg());
        verify(paymentRecordService, never()).update(org.mockito.ArgumentMatchers.<Wrapper<PaymentRecord>>any());
    }

    private Map<?, ?> readUpdateValues(Wrapper<PaymentRecord> wrapper) {
        try {
            java.lang.reflect.Field field = wrapper.getClass().getSuperclass().getDeclaredField("paramNameValuePairs");
            field.setAccessible(true);
            return (Map<?, ?>) field.get(wrapper);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
