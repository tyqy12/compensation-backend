package com.yiyundao.compensation.common.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecretLogSanitizerTest {

    @Test
    void shouldMaskSecretsInUrlQueryMessage() {
        String message = "GET https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=corp-a&corpsecret=secret-value"
                + "&access_token=token-value failed";

        String sanitized = SecretLogSanitizer.sanitize(message);

        assertThat(sanitized).contains("corpid=corp-a");
        assertThat(sanitized).contains("corpsecret=***");
        assertThat(sanitized).contains("access_token=***");
        assertThat(sanitized).doesNotContain("secret-value");
        assertThat(sanitized).doesNotContain("token-value");
    }

    @Test
    void shouldMaskSecretsInJsonLikeMessage() {
        String message = "POST /token body={\"app_id\":\"app-a\",\"app_secret\":\"secret-value\","
                + "\"tenant_access_token\":\"tenant-token\"}";

        String sanitized = SecretLogSanitizer.sanitize(message);

        assertThat(sanitized).contains("\"app_id\":\"app-a\"");
        assertThat(sanitized).contains("\"app_secret\":\"***\"");
        assertThat(sanitized).contains("\"tenant_access_token\":\"***\"");
        assertThat(sanitized).doesNotContain("secret-value");
        assertThat(sanitized).doesNotContain("tenant-token");
    }

    @Test
    void shouldMaskSensitiveKeyFields() {
        String message = "body={\"apiKey\":\"api-key-value\",\"accessKeySecret\":\"access-secret-value\","
                + "\"des3Key\":\"des3-value\",\"aesKey\":\"aes-value\",\"sm4Key\":\"sm4-value\"}";

        String sanitized = SecretLogSanitizer.sanitize(message);

        assertThat(sanitized)
                .contains("\"apiKey\":\"***\"")
                .contains("\"accessKeySecret\":\"***\"")
                .contains("\"des3Key\":\"***\"")
                .contains("\"aesKey\":\"***\"")
                .contains("\"sm4Key\":\"***\"");
        assertThat(sanitized)
                .doesNotContain("api-key-value")
                .doesNotContain("access-secret-value")
                .doesNotContain("des3-value")
                .doesNotContain("aes-value")
                .doesNotContain("sm4-value");
    }

    @Test
    void shouldMaskAuthorizationBearerToken() {
        String message = "headers={Authorization: Bearer bearer-token, proxyAuthorization=Basic basic-token}";

        String sanitized = SecretLogSanitizer.sanitize(message);

        assertThat(sanitized).contains("Authorization: Bearer ***");
        assertThat(sanitized).contains("proxyAuthorization=Basic ***");
        assertThat(sanitized).doesNotContain("bearer-token");
        assertThat(sanitized).doesNotContain("basic-token");
    }

    @Test
    void shouldMaskOAuthCallbackCodeAndStateWithoutMaskingBusinessCode() {
        String message = "GET /auth/oauth/callback/wechat?code=oauth-code-value&state=state-value"
                + "&employeeCode=E001&status=active";

        String sanitized = SecretLogSanitizer.sanitize(message);

        assertThat(sanitized)
                .contains("code=***")
                .contains("state=***")
                .contains("employeeCode=E001")
                .contains("status=active");
        assertThat(sanitized)
                .doesNotContain("oauth-code-value")
                .doesNotContain("state-value");
    }

    @Test
    void shouldMaskSecretsInMapValues() {
        Map<String, Object> sanitized = SecretLogSanitizer.sanitize(Map.of(
                "errcode", 40001,
                "errmsg", "invalid credential, access_token=token-value",
                "access_token", "token-value",
                "appsecret", "secret-value",
                "state", "oauth-state-value"
        ));

        assertThat(sanitized.get("errcode")).isEqualTo(40001);
        assertThat(sanitized.get("access_token")).isEqualTo("***");
        assertThat(sanitized.get("appsecret")).isEqualTo("***");
        assertThat(sanitized.get("state")).isEqualTo("***");
        assertThat(sanitized.get("errmsg")).isEqualTo("invalid credential, access_token=***");
    }

    @Test
    void shouldMaskThrowableMessageWithoutLosingExceptionTypeWhenMessageMissing() {
        RuntimeException exception = new RuntimeException(
                "401 from GET /gettoken?appsecret=secret-value&token=token-value"
        );

        String sanitized = SecretLogSanitizer.sanitize(exception);

        assertThat(sanitized).contains("appsecret=***");
        assertThat(sanitized).contains("token=***");
        assertThat(sanitized).doesNotContain("secret-value");
        assertThat(sanitized).doesNotContain("token-value");
        assertThat(SecretLogSanitizer.sanitize(new RuntimeException())).isEqualTo("RuntimeException");
    }
}
