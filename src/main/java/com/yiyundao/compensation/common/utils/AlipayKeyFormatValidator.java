package com.yiyundao.compensation.common.utils;

import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;

public final class AlipayKeyFormatValidator {

    private static final int MIN_PKCS8_RSA_PRIVATE_KEY_BASE64_LENGTH = 512;
    private static final String INVALID_PRIVATE_KEY_MESSAGE =
            "支付宝应用私钥格式错误：请配置 PKCS8 格式 RSA 私钥，不能使用 test-private-key/your_private_key 等占位值";

    private AlipayKeyFormatValidator() {
    }

    public static String normalizePkcs8PrivateKey(String privateKey) {
        if (!StringUtils.hasText(privateKey)) {
            throw new IllegalStateException("支付宝配置不完整：缺少应用私钥(privateKey)");
        }

        String normalized = stripPemAndWhitespace(privateKey);
        if (isPlaceholder(privateKey) || normalized.length() < MIN_PKCS8_RSA_PRIVATE_KEY_BASE64_LENGTH) {
            throw new IllegalStateException(INVALID_PRIVATE_KEY_MESSAGE);
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(INVALID_PRIVATE_KEY_MESSAGE, ex);
        }

        try {
            KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception ex) {
            throw new IllegalStateException(INVALID_PRIVATE_KEY_MESSAGE, ex);
        }
        return normalized;
    }

    private static String stripPemAndWhitespace(String privateKey) {
        return privateKey.trim()
                .replace("\\r", "\r")
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
    }

    private static boolean isPlaceholder(String value) {
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("***")
                || trimmed.equals("your_private_key")
                || trimmed.equals("your-private-key")
                || trimmed.equals("test-private-key")
                || trimmed.equals("test_private_key")
                || trimmed.equals("private-key")
                || trimmed.equals("private_key")
                || trimmed.startsWith("your_")
                || trimmed.startsWith("test-")
                || trimmed.contains("placeholder");
    }
}
