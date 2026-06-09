package com.yiyundao.compensation.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private final MockEnvironment environment = new MockEnvironment();
    private final ClientIpResolver resolver = new ClientIpResolver(environment);

    @Test
    void shouldUseRemoteAddrWhenProxyIsNotTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.5, 10.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void shouldUseForwardedForWhenRemoteAddrMatchesTrustedProxy() {
        environment.setProperty("security.trusted-proxies", "10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Forwarded-For", "198.51.100.5, 10.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.5");
    }

    @Test
    void shouldFallbackToRealIpForTrustedProxyWhenForwardedForIsEmpty() {
        environment.setProperty("security.trusted-proxies", "10.1.2.3");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Forwarded-For", "unknown");
        request.addHeader("X-Real-IP", "198.51.100.8");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.8");
    }

    @Test
    void shouldSupportTrustedProxyFromEnvironmentStyleProperty() {
        environment.setProperty("SECURITY_TRUSTED_PROXIES", "10.1.2.3");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Forwarded-For", "198.51.100.9");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.9");
    }
}
