package com.yiyundao.compensation.interfaces.controller.payment;

import com.yiyundao.compensation.modules.payment.provider.SettlementCallbackResult;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
public class PaymentNotifyController {

    private final SettlementService settlementService;

    // 支付宝异步通知回调（已在安全配置中放行 /alipay/notify）
    @PostMapping("/alipay/notify")
    public String alipayNotify(HttpServletRequest request) {
        Map<String, String[]> paramMap = request.getParameterMap();

        // 转换为Map<String, String>格式用于签名验证
        Map<String, String> params = paramMap.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue()[0] // 取第一个值
                ));

        log.info("收到支付宝通知: {}", params);

        try {
            SettlementCallbackResult result = settlementService.handleCallback("alipay", params);
            return result.isSuccess() ? "success" : "failure";
        } catch (Exception e) {
            log.error("处理支付宝通知失败", e);
            return "failure";
        }
    }
}
