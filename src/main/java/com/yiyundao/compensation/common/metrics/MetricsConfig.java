package com.yiyundao.compensation.common.metrics;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 监控配置
 * <p>
 * 配置 Micrometer 指标采集和 AOP 计时支持。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Configuration
public class MetricsConfig {

    /**
     * 默认的 MeterRegistry（开发环境使用 SimpleMeterRegistry）
     * <p>
     * 生产环境建议通过 Actuator 自动配置或使用 PrometheusMeterRegistry。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    /**
     * 启用 @Timed 注解支持
     */
    @Bean
    @ConditionalOnClass(TimedAspect.class)
    @ConditionalOnMissingBean(TimedAspect.class)
    public TimedAspect timedAspect(MeterRegistry meterRegistry) {
        return new TimedAspect(meterRegistry);
    }
}
