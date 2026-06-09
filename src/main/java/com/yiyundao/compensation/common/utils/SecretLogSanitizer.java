package com.yiyundao.compensation.common.utils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SecretLogSanitizer {

    private static final String MASKED = "***";

    private static final Set<String> SECRET_KEYS = Set.of(
            "access_token",
            "refresh_token",
            "token",
            "api_key",
            "apikey",
            "app_key",
            "appkey",
            "access_key",
            "accesskey",
            "access_key_id",
            "accesskeyid",
            "access_key_secret",
            "accesskeysecret",
            "appsecret",
            "corpsecret",
            "app_secret",
            "client_secret",
            "secret",
            "secret_key",
            "secretkey",
            "aes_key",
            "aeskey",
            "sm4_key",
            "sm4key",
            "des3_key",
            "des3key",
            "3des_key",
            "3deskey",
            "encrypt_key",
            "encryptkey",
            "sign_key",
            "signkey",
            "password",
            "passwd",
            "credential",
            "private_key",
            "authorization",
            "code",
            "state",
            "oauth_code",
            "auth_code",
            "authorization_code",
            "oauth_state"
    );

    private static final String SECRET_NAME_PATTERN =
            "access[_-]?token|refresh[_-]?token|"
                    + "(?:app|api|access|secret|aes|sm4|des3|3des|encrypt|sign)[_-]?key(?:[_-]?(?:id|secret))?|"
                    + "appsecret|corpsecret|app[_-]?secret|client[_-]?secret|secret|password|passwd|credential|"
                    + "private[_-]?key|\\btoken\\b|\\bcode\\b|\\bstate\\b|oauth[_-]?code|auth[_-]?code|"
                    + "authorization[_-]?code|oauth[_-]?state";

    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)(^|[?&\\s,{])([A-Za-z0-9_.-]*(?:" + SECRET_NAME_PATTERN + ")[A-Za-z0-9_.-]*)(\\s*[=:]\\s*)([^\\s&,}]+)"
    );
    private static final Pattern QUOTED_KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)([\"'])([A-Za-z0-9_.-]*(?:authorization|" + SECRET_NAME_PATTERN + ")[A-Za-z0-9_.-]*)([\"']\\s*:\\s*[\"'])([^\"']*)([\"'])"
    );
    private static final Pattern AUTHORIZATION_HEADER_PATTERN = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*(?:bearer|basic)?\\s*)([^\\s,;}]+)"
    );

    private SecretLogSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String maskedAuthorization = replaceMatches(value, AUTHORIZATION_HEADER_PATTERN, 2);
        String maskedQuoted = replaceMatches(maskedAuthorization, QUOTED_KEY_VALUE_PATTERN, 4);
        Matcher matcher = KEY_VALUE_PATTERN.matcher(maskedQuoted);
        StringBuilder sanitized = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sanitized, Matcher.quoteReplacement(
                    matcher.group(1) + matcher.group(2) + matcher.group(3) + MASKED
            ));
        }
        matcher.appendTail(sanitized);
        return sanitized.toString();
    }

    public static String sanitize(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return sanitize(message);
    }

    private static String replaceMatches(String value, Pattern pattern, int secretGroup) {
        Matcher matcher = pattern.matcher(value);
        StringBuilder sanitized = new StringBuilder();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder();
            for (int i = 1; i <= matcher.groupCount(); i++) {
                replacement.append(i == secretGroup ? MASKED : matcher.group(i));
            }
            matcher.appendReplacement(sanitized, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(sanitized);
        return sanitized.toString();
    }

    public static Map<String, Object> sanitize(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return values.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> sanitizeValue(entry.getKey(), entry.getValue()),
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                ));
    }

    private static Object sanitizeValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        String normalizedKey = key == null ? "" : key.trim()
                .replace('-', '_')
                .replace('.', '_')
                .toLowerCase(Locale.ROOT);
        if (SECRET_KEYS.contains(normalizedKey) || containsSecretToken(normalizedKey)) {
            return MASKED;
        }
        if (value instanceof CharSequence text) {
            return sanitize(text.toString());
        }
        return value;
    }

    private static boolean containsSecretToken(String key) {
        return key.contains("token")
                || key.contains("secret")
                || key.contains("password")
                || key.contains("passwd")
                || key.contains("credential")
                || key.contains("private_key")
                || key.contains("privatekey")
                || "code".equals(key)
                || "state".equals(key)
                || key.contains("oauth_code")
                || key.contains("auth_code")
                || key.contains("authorization_code")
                || key.contains("oauth_state")
                || containsSensitiveKeyName(key);
    }

    private static boolean containsSensitiveKeyName(String key) {
        return key.contains("apikey")
                || key.contains("api_key")
                || key.contains("appkey")
                || key.contains("app_key")
                || key.contains("accesskey")
                || key.contains("access_key")
                || key.contains("secretkey")
                || key.contains("secret_key")
                || key.contains("aeskey")
                || key.contains("aes_key")
                || key.contains("sm4key")
                || key.contains("sm4_key")
                || key.contains("des3key")
                || key.contains("des3_key")
                || key.contains("3deskey")
                || key.contains("3des_key")
                || key.contains("encryptkey")
                || key.contains("encrypt_key")
                || key.contains("signkey")
                || key.contains("sign_key");
    }
}
