package com.yiyundao.compensation.interfaces.controller.payment;

import com.yiyundao.compensation.modules.payment.provider.SettlementCallbackResult;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import com.yiyundao.compensation.modules.payment.support.PaymentCallbackLogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
public class PaymentNotifyController {

    private final SettlementService settlementService;

    // 支付宝异步通知回调（已在安全配置中放行 /alipay/notify）
    @PostMapping("/alipay/notify")
    public String alipayNotify(HttpServletRequest request) {
        Map<String, String> params = flattenParameterMap(request.getParameterMap());

        log.info("收到支付宝通知: {}", PaymentCallbackLogSanitizer.sanitize(params));

        try {
            SettlementCallbackResult result = settlementService.handleCallback("alipay", params);
            return result != null && result.isSuccess() ? "success" : "failure";
        } catch (Exception e) {
            log.error("处理支付宝通知失败: errorType={}", e.getClass().getSimpleName());
            return "failure";
        }
    }

    private Map<String, String> flattenParameterMap(Map<String, String[]> paramMap) {
        if (paramMap == null || paramMap.isEmpty()) {
            return Map.of();
        }
        Map<String, String> params = new LinkedHashMap<>();
        paramMap.forEach((key, values) -> params.put(key, firstValue(values)));
        return params;
    }

    private String firstValue(String[] values) {
        return values != null && values.length > 0 ? values[0] : "";
    }
}
