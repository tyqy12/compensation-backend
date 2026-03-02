package com.yiyundao.compensation.modules.payment.provider.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentRecordService).updateById(captor.capture());
        PaymentRecord updated = captor.getValue();
        assertEquals(1001L, updated.getId());
        assertEquals("yunzhanghu", updated.getProviderCode());
        assertEquals("YZH_ORDER_1001", updated.getProviderOrderNo());
        assertEquals("YZH_REF_001", updated.getProviderTradeNo());
        assertEquals(PaymentStatus.PROCESSING, updated.getStatus());
    }

    @Test
    @DisplayName("单笔发放失败：缺少身份证信息")
    void singleTransfer_shouldFailWhenIdCardMissing() {
        SettlementRequest request = SettlementRequest.builder()
                .paymentRecordId(1002L)
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
        verify(paymentRecordService).updateById(any(PaymentRecord.class));
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
    @DisplayName("回调处理：验签通过后更新支付记录")
    void callback_shouldUpdateRecordWhenVerified() {
        NotifyOrderData notifyData = new NotifyOrderData();
        notifyData.setOrderId("YZH_ORDER_CB_1");
        notifyData.setRef("YZH_REF_CB_1");
        notifyData.setStatus("SUCCESS");
        notifyData.setStatusDetail("PAY_SUCCESS");

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
        when(paymentRecordService.getByProviderOrderNo("yunzhanghu", "YZH_ORDER_CB_1")).thenReturn(record);

        boolean verified = provider.verifyCallback(Map.of("data", "x", "mess", "m", "timestamp", "t", "sign", "s"));
        SettlementCallbackResult callbackResult = provider.handleCallback(Map.of());

        assertTrue(verified);
        assertTrue(callbackResult.isSuccess());
        assertEquals("YZH_ORDER_CB_1", callbackResult.getBizNo());
        assertEquals(SettlementStatus.SUCCESS, callbackResult.getStatus());

        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentRecordService).updateById(captor.capture());
        assertEquals(PaymentStatus.SUCCESS, captor.getValue().getStatus());
    }
}
