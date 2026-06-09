package com.yiyundao.compensation.interceptor;

import com.yiyundao.compensation.common.annotation.Idempotent;
import com.yiyundao.compensation.security.ClientIpResolver;
import com.yiyundao.compensation.service.IdempotentService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentInterceptorTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseRemoteAddrWhenForwardedForIsNotFromTrustedProxy() throws Exception {
        IdempotentService idempotentService = mock(IdempotentService.class);
        when(idempotentService.generateKey(eq("#ip"), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn("203.0.113.10");
        when(idempotentService.tryLock("203.0.113.10", 300, 0)).thenReturn(true);
        IdempotentInterceptor interceptor = new IdempotentInterceptor(
                idempotentService,
                new ClientIpResolver(new MockEnvironment())
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/payroll/batches/1/lock");
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.7");
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), "submit");

        interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod);

        verify(idempotentService).generateKey(eq("#ip"), org.mockito.ArgumentMatchers.<Map<String, Object>>argThat(vars ->
                "203.0.113.10".equals(vars.get("ip"))
        ));
        verify(idempotentService).tryLock("203.0.113.10", 300, 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseForwardedForWhenRemoteAddrIsTrustedProxy() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("security.trusted-proxies", "10.0.0.0/8");
        IdempotentService idempotentService = mock(IdempotentService.class);
        when(idempotentService.generateKey(eq("#ip"), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn("198.51.100.7");
        when(idempotentService.tryLock("198.51.100.7", 300, 0)).thenReturn(true);
        IdempotentInterceptor interceptor = new IdempotentInterceptor(
                idempotentService,
                new ClientIpResolver(environment)
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/payroll/batches/1/lock");
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.9");
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), "submit");

        interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod);

        verify(idempotentService).generateKey(eq("#ip"), org.mockito.ArgumentMatchers.<Map<String, Object>>argThat(vars ->
                "198.51.100.7".equals(vars.get("ip"))
        ));
        verify(idempotentService).tryLock("198.51.100.7", 300, 0);
    }

    private static class TestController {
        @Idempotent(key = "#ip")
        public void submit() {
        }
    }
}
