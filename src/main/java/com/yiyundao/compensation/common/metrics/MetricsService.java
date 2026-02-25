package com.yiyundao.compensation.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 指标服务
 * <p>
 * 封装 Micrometer 指标操作，提供简化的指标采集接口。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
@Component
public class MetricsService {

    private final MeterRegistry meterRegistry;

    /**
     * 计数器缓存
     */
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    /**
     * 计时器缓存
     */
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();

    /**
     * 计量器缓存
     */
    private final Map<String, Number> gaugeCache = new ConcurrentHashMap<>();

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // ==================== 计数器 ====================

    /**
     * 创建或获取计数器
     *
     * @param name        指标名称
     * @param description 描述
     * @param tags        标签
     * @return 计数器
     */
    public Counter counter(String name, String description, Map<String, String> tags) {
        String key = buildKey(name, tags);
        return counterCache.computeIfAbsent(key, k -> {
            Counter.Builder builder = Counter.builder(name)
                    .description(description);

            if (tags != null) {
                tags.forEach(builder::tag);
            }

            return builder.register(meterRegistry);
        });
    }

    /**
     * 创建或获取计数器（无标签）
     *
     * @param name        指标名称
     * @param description 描述
     * @return 计数器
     */
    public Counter counter(String name, String description) {
        return counter(name, description, null);
    }

    /**
     * 增加计数器
     *
     * @param name   指标名称
     * @param amount 增量
     * @param tags   标签
     */
    public void incrementCounter(String name, double amount, Map<String, String> tags) {
        counter(name, null, tags).increment(amount);
    }

    /**
     * 增加计数器（+1）
     *
     * @param name 指标名称
     * @param tags 标签
     */
    public void incrementCounter(String name, Map<String, String> tags) {
        incrementCounter(name, 1.0, tags);
    }

    /**
     * 增加计数器（+1，无标签）
     *
     * @param name 指标名称
     */
    public void incrementCounter(String name) {
        incrementCounter(name, 1.0, null);
    }

    // ==================== 计时器 ====================

    /**
     * 创建或获取计时器
     *
     * @param name        指标名称
     * @param description 描述
     * @param tags        标签
     * @return 计时器
     */
    public Timer timer(String name, String description, Map<String, String> tags) {
        String key = buildKey(name, tags);
        return timerCache.computeIfAbsent(key, k -> {
            Timer.Builder builder = Timer.builder(name)
                    .description(description);

            if (tags != null) {
                tags.forEach(builder::tag);
            }

            return builder.register(meterRegistry);
        });
    }

    /**
     * 创建或获取计时器（无标签）
     *
     * @param name        指标名称
     * @param description 描述
     * @return 计时器
     */
    public Timer timer(String name, String description) {
        return timer(name, description, null);
    }

    /**
     * 记录耗时
     *
     * @param name      指标名称
     * @param duration  时长
     * @param unit      时间单位
     * @param tags      标签
     */
    public void recordTimer(String name, long duration, TimeUnit unit, Map<String, String> tags) {
        timer(name, null, tags).record(duration, unit);
    }

    /**
     * 记录耗时（毫秒）
     *
     * @param name     指标名称
     * @param duration 时长（毫秒）
     * @param tags     标签
     */
    public void recordTimerMs(String name, long duration, Map<String, String> tags) {
        recordTimer(name, duration, TimeUnit.MILLISECONDS, tags);
    }

    /**
     * 使用计时器执行代码
     *
     * @param name      指标名称
     * @param tags      标签
     * @param supplier  执行代码
     * @param <T>       返回类型
     * @return 执行结果
     */
    public <T> T recordTimer(String name, Map<String, String> tags, Supplier<T> supplier) {
        Timer timer = timer(name, null, tags);
        return timer.record(supplier);
    }

    /**
     * 使用计时器执行代码（无标签）
     *
     * @param name     指标名称
     * @param supplier 执行代码
     * @param <T>      返回类型
     * @return 执行结果
     */
    public <T> T recordTimer(String name, Supplier<T> supplier) {
        return recordTimer(name, null, supplier);
    }

    // ==================== 计量器 ====================

    /**
     * 创建计量器
     *
     * @param name        指标名称
     * @param description 描述
     * @param tags        标签
     * @param valueProvider 值提供者
     */
    public void gauge(String name, String description, Map<String, String> tags,
                      Supplier<Number> valueProvider) {
        Gauge.Builder<?> builder = Gauge.builder(name, valueProvider);

        if (tags != null) {
            tags.forEach(builder::tag);
        }

        if (description != null) {
            builder.description(description);
        }

        builder.register(meterRegistry);
    }

    /**
     * 创建计量器（无标签）
     *
     * @param name        指标名称
     * @param description 描述
     * @param valueProvider 值提供者
     */
    public void gauge(String name, String description, Supplier<Number> valueProvider) {
        gauge(name, description, null, valueProvider);
    }

    /**
     * 更新计量器值
     *
     * @param name  指标名称
     * @param value 值
     * @param tags  标签
     */
    public void gaugeValue(String name, Number value, Map<String, String> tags) {
        String key = buildKey(name, tags);
        gaugeCache.put(key, value);
        gauge(name, null, tags, () -> value);
    }

    // ==================== 业务指标 ====================

    /**
     * 记录 API 请求
     *
     * @param uri     请求路径
     * @param method  请求方法
     * @param status  响应状态
     * @param duration 耗时（毫秒）
     */
    public void recordApiRequest(String uri, String method, int status, long duration) {
        incrementCounter("api.request.total", Map.of(
                "uri", normalizeUri(uri),
                "method", method,
                "status", String.valueOf(status)
        ));
        recordTimerMs("api.request.duration", duration, Map.of(
                "uri", normalizeUri(uri),
                "method", method
        ));
    }

    /**
     * 记录业务事件
     *
     * @param event   事件名称
     * @param tags    标签
     */
    public void recordBusinessEvent(String event, Map<String, String> tags) {
        incrementCounter("business.event", tags);
    }

    /**
     * 记录业务事件（带数量）
     *
     * @param event  事件名称
     * @param count  数量
     * @param tags   标签
     */
    public void recordBusinessEvent(String event, double count, Map<String, String> tags) {
        incrementCounter("business.event", count, tags);
    }

    /**
     * 记录异常
     *
     * @param exceptionType 异常类型
     * @param tags          标签
     */
    public void recordException(String exceptionType, Map<String, String> tags) {
        incrementCounter("exception.total", Map.of(
                "type", exceptionType
        ));
    }

    /**
     * 记录数据库查询
     *
     * @param operation   操作类型
     * @param duration    耗时（毫秒）
     * @param success     是否成功
     */
    public void recordDbQuery(String operation, long duration, boolean success) {
        Map<String, String> tags = Map.of(
                "operation", operation,
                "success", String.valueOf(success)
        );
        incrementCounter("db.query.total", tags);
        recordTimerMs("db.query.duration", duration, tags);
    }

    /**
     * 记录缓存操作
     *
     * @param operation   操作类型
     * @param hit         是否命中
     */
    public void recordCache(String operation, boolean hit) {
        Map<String, String> tags = Map.of(
                "operation", operation,
                "hit", String.valueOf(hit)
        );
        incrementCounter("cache.operation.total", tags);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建缓存键
     */
    private String buildKey(String name, Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return name;
        }
        return name + ":" + tags.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    /**
     * 规范化 URI（移除路径参数）
     */
    private String normalizeUri(String uri) {
        if (uri == null) {
            return "unknown";
        }
        // 简化路径参数
        return uri.replaceAll("/\\d+", "/{id}")
                .replaceAll("/[a-f0-9-]{36}", "/{uuid}");
    }
}
