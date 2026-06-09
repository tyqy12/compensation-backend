package com.yiyundao.compensation.interfaces.controller.payment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.modules.payment.provider.SettlementCallbackResult;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import com.yiyundao.compensation.modules.payment.support.PaymentCallbackLogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/settlement/callback")
@RequiredArgsConstructor
public class SettlementCallbackController {

    private final SettlementService settlementService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{providerCode}")
    public ResponseEntity<String> handleCallback(@PathVariable String providerCode,
                                                 @RequestParam Map<String, String> params,
                                                 @RequestBody(required = false) String body) {
        Map<String, String> allParams = new HashMap<>(params);
        allParams.putAll(parseBody(body));
        log.info("收到渠道回调: provider={}, params={}", providerCode, PaymentCallbackLogSanitizer.sanitize(allParams));

        try {
            SettlementCallbackResult result = settlementService.handleCallback(providerCode, allParams);
            if (result.isSuccess()) {
                return ResponseEntity.ok("SUCCESS");
            }
            log.warn("渠道回调处理失败: provider={}", providerCode);
            return ResponseEntity.internalServerError().body("FAIL");
        } catch (Exception e) {
            log.error("回调处理异常: provider={}, errorType={}", providerCode, e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().body("ERROR");
        }
    }

    private Map<String, String> parseBody(String body) {
        if (!StringUtils.hasText(body)) {
            return Map.of();
        }
        String trimmed = body.trim();
        try {
            if (trimmed.startsWith("{")) {
                Map<String, Object> value = objectMapper.readValue(trimmed, new TypeReference<>() {});
                Map<String, String> result = new HashMap<>();
                value.forEach((k, v) -> result.put(k, v == null ? null : String.valueOf(v)));
                return result;
            }

            Map<String, String> result = new HashMap<>();
            String[] pairs = trimmed.split("&");
            for (String pair : pairs) {
                if (!StringUtils.hasText(pair)) {
                    continue;
                }
                String[] kv = pair.split("=", 2);
                String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                result.put(k, v);
            }
            return result;
        } catch (Exception e) {
            log.warn("解析回调body失败，已忽略body内容: {}", e.getMessage());
            return Map.of();
        }
    }
}
