package com.yiyundao.compensation.interfaces.controller.payment;

import com.yiyundao.compensation.service.AlipayService;
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

    private final AlipayService alipayService;

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

        // 验证支付宝签名
        if (!alipayService.verifyNotification(params)) {
            log.warn("支付宝通知验签失败");
            return "failure";
        }

        String outTradeNo = request.getParameter("out_biz_no");
        String tradeNo = request.getParameter("trade_no");
        String tradeStatus = request.getParameter("trade_status");

        try {
            alipayService.handleNotification(outTradeNo, tradeNo, tradeStatus);
            return "success";
        } catch (Exception e) {
            log.error("处理支付宝通知失败", e);
            return "failure";
        }
    }
}

