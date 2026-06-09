package com.yiyundao.compensation.common.config;

import com.yiyundao.compensation.interceptor.IdempotentInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<IdempotentInterceptor> idempotentInterceptorProvider;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        IdempotentInterceptor idempotentInterceptor = idempotentInterceptorProvider.getIfAvailable();
        if (idempotentInterceptor != null) {
            registry.addInterceptor(idempotentInterceptor)
                    .addPathPatterns("/api/**")
                    .excludePathPatterns("/api/v*/auth/**", "/api/v*/health/**");
        }
    }

    @Bean(name = "webMvcTaskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("task-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
