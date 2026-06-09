package com.yiyundao.compensation.common.config;

import com.yiyundao.compensation.interceptor.IdempotentInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebMvcConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void addInterceptorsShouldSkipIdempotentInterceptorWhenBeanMissing() {
        ObjectProvider<IdempotentInterceptor> provider = mock(ObjectProvider.class);
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        when(provider.getIfAvailable()).thenReturn(null);

        new WebMvcConfig(provider).addInterceptors(registry);

        verify(registry, never()).addInterceptor(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void addInterceptorsShouldRegisterIdempotentInterceptorWhenBeanExists() {
        IdempotentInterceptor interceptor = mock(IdempotentInterceptor.class);
        ObjectProvider<IdempotentInterceptor> provider = mock(ObjectProvider.class);
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);
        when(provider.getIfAvailable()).thenReturn(interceptor);
        when(registry.addInterceptor(interceptor)).thenReturn(registration);
        when(registration.addPathPatterns("/api/**")).thenReturn(registration);

        new WebMvcConfig(provider).addInterceptors(registry);

        verify(registry).addInterceptor(interceptor);
        verify(registration).addPathPatterns("/api/**");
        verify(registration).excludePathPatterns("/api/v*/auth/**", "/api/v*/health/**");
    }
}
