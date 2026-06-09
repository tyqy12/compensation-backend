package com.yiyundao.compensation.modules.payment.support;

import com.yiyundao.compensation.common.annotation.SensitiveType;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PaymentCallbackLogSanitizer {

    private static final String MASKED = "***";

    private static final Set<String> SECRET_KEYS = Set.of(
            "sign",
            "signature",
            "token",
            "access_token",
            "refresh_token",
            "secret",
            "secret_key",
            "private_key",
            "public_key",
            "app_key",
            "app_secret",
            "des3_key",
            "data",
            "mess",
            "biz_content",
            "ciphertext",
            "encrypt"
    );

    private static final Set<String> ORDER_KEYS = Set.of(
            "out_biz_no",
            "trade_no",
            "out_trade_no",
            "order_id",
            "orderid",
            "provider_order_no",
            "provider_trade_no",
            "biz_no",
            "notify_id"
    );

    private PaymentCallbackLogSanitizer() {
    }

    public static Map<String, String> sanitize(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        params.forEach((key, value) -> sanitized.put(key, sanitizeValue(key, value)));
        return sanitized;
    }

    public static String sanitizeField(String key, String value) {
        return sanitizeValue(key, value);
    }

    private static String sanitizeValue(String key, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String normalizedKey = normalizeKey(key);
        if (SECRET_KEYS.contains(normalizedKey) || containsAny(normalizedKey, "password", "passwd", "credential")) {
            return MASKED;
        }
        if (ORDER_KEYS.contains(normalizedKey) || normalizedKey.endsWith("_no") || normalizedKey.endsWith("_id")) {
            return maskKeep(value, 6, 4);
        }
        if (containsAny(normalizedKey, "account", "card", "identity", "cert", "idcard")) {
            return maskAccountLike(value);
        }
        if (containsAny(normalizedKey, "phone", "mobile")) {
            return SensitiveType.PHONE.desensitize(value);
        }
        if (containsAny(normalizedKey, "email", "logon")) {
            return SensitiveType.EMAIL.desensitize(value);
        }
        if (containsAny(normalizedKey, "name", "recipient", "payee")) {
            return SensitiveType.NAME.desensitize(value);
        }
        return value;
    }

    private static String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        return key.trim()
                .replace('-', '_')
                .replace('.', '_')
                .toLowerCase(Locale.ROOT);
    }

    private static String maskAccountLike(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (value.contains("@")) {
            return SensitiveType.EMAIL.desensitize(value);
        }
        String digitsOnly = value.replaceAll("\\s+", "");
        if (digitsOnly.matches("\\d{11}")) {
            return SensitiveType.PHONE.desensitize(digitsOnly);
        }
        if (digitsOnly.matches("\\d{12,}")) {
            return SensitiveType.BANK_CARD.desensitize(digitsOnly);
        }
        return maskKeep(value, 2, 2);
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String maskKeep(String value, int keepPrefix, int keepSuffix) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= keepPrefix + keepSuffix) {
            return MASKED;
        }
        return value.substring(0, keepPrefix)
                + "*".repeat(value.length() - keepPrefix - keepSuffix)
                + value.substring(value.length() - keepSuffix);
    }
}
