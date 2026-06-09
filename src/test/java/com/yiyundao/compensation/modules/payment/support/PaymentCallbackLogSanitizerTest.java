package com.yiyundao.compensation.modules.payment.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("支付回调日志脱敏测试")
class PaymentCallbackLogSanitizerTest {

    @Test
    @DisplayName("签名与密文字段应完全隐藏")
    void sanitize_shouldMaskSecretCallbackFields() {
        Map<String, String> sanitized = PaymentCallbackLogSanitizer.sanitize(Map.of(
                "sign", "raw-signature",
                "data", "encrypted-data",
                "mess", "encrypted-key",
                "biz_content", "{\"account\":\"payee@example.com\"}"
        ));

        assertEquals("***", sanitized.get("sign"));
        assertEquals("***", sanitized.get("data"));
        assertEquals("***", sanitized.get("mess"));
        assertEquals("***", sanitized.get("biz_content"));
    }

    @Test
    @DisplayName("订单号保留少量首尾字符用于排查")
    void sanitize_shouldPartiallyMaskOrderIds() {
        Map<String, String> sanitized = PaymentCallbackLogSanitizer.sanitize(Map.of(
                "out_biz_no", "COMP_202606020101_ABCDEFGH",
                "trade_no", "202606022200111222333444"
        ));

        assertEquals("COMP_2****************EFGH", sanitized.get("out_biz_no"));
        assertEquals("202606**************3444", sanitized.get("trade_no"));
        assertEquals("202606**************3444",
                PaymentCallbackLogSanitizer.sanitizeField("trade_no", "202606022200111222333444"));
    }

    @Test
    @DisplayName("账号、手机号和姓名按敏感类型脱敏")
    void sanitize_shouldMaskAccountPhoneAndName() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("recipient_account", "payee@example.com");
        params.put("mobile", "13812345678");
        params.put("payee_name", "张三");
        params.put("trade_status", "TRADE_SUCCESS");

        Map<String, String> sanitized = PaymentCallbackLogSanitizer.sanitize(params);

        assertEquals("pa**@example.com", sanitized.get("recipient_account"));
        assertEquals("138****5678", sanitized.get("mobile"));
        assertEquals("张*", sanitized.get("payee_name"));
        assertEquals("TRADE_SUCCESS", sanitized.get("trade_status"));
    }
}
