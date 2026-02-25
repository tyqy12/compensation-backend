package com.yiyundao.compensation.common.trace;

import brave.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 链路追踪上下文
 * <p>
 * 提供链路追踪的上下文管理，支持：
 * - TraceId 和 SpanId 管理
 * - MDC 集成
 * - 手动 Span 创建
 * </p>
 * <p>
 * 注意：此组件仅在 Brave Tracer 可用时加载。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
@Component
@ConditionalOnBean(Tracer.class)
public class TraceContext {

    /**
     * TraceId 键
     */
    public static final String TRACE_ID_KEY = "traceId";

    /**
     * SpanId 键
     */
    public static final String SPAN_ID_KEY = "spanId";

    /**
     * 父 SpanId 键
     */
    public static final String PARENT_SPAN_ID_KEY = "parentSpanId";

    /**
     * 自定义上下文数据
     */
    private final Map<String, Object> customContext = new ConcurrentHashMap<>();

    private final Tracer tracer;

    public TraceContext(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * 获取当前 TraceId
     *
     * @return TraceId
     */
    public String getTraceId() {
        // 优先从 MDC 获取
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId != null && !traceId.isEmpty()) {
            return traceId;
        }

        // 从当前 Span 获取
        if (tracer != null && tracer.currentSpan() != null) {
            brave.Span currentSpan = tracer.currentSpan();
            traceId = currentSpan.context().traceIdString();
            if (traceId != null) {
                MDC.put(TRACE_ID_KEY, traceId);
                return traceId;
            }
        }

        return null;
    }

    /**
     * 获取当前 SpanId
     *
     * @return SpanId
     */
    public String getSpanId() {
        // 优先从 MDC 获取
        String spanId = MDC.get(SPAN_ID_KEY);
        if (spanId != null && !spanId.isEmpty()) {
            return spanId;
        }

        // 从当前 Span 获取
        if (tracer != null && tracer.currentSpan() != null) {
            brave.Span currentSpan = tracer.currentSpan();
            spanId = currentSpan.context().spanIdString();
            if (spanId != null) {
                MDC.put(SPAN_ID_KEY, spanId);
                return spanId;
            }
        }

        return null;
    }

    /**
     * 创建新的 Span
     *
     * @param name Span 名称
     * @return Span
     */
    public brave.Span newSpan(String name) {
        if (tracer == null) {
            return null;
        }
        return tracer.newChild(tracer.currentSpan().context()).name(name).start();
    }

    /**
     * 创建新的 Span（带标签）
     *
     * @param name  Span 名称
     * @param tags  标签
     * @return Span
     */
    public brave.Span newSpan(String name, Map<String, String> tags) {
        brave.Span span = newSpan(name);
        if (span != null && tags != null) {
            tags.forEach(span::tag);
        }
        return span;
    }

    /**
     * 执行带追踪的操作
     *
     * @param name     Span 名称
     * @param runnable 操作
     */
    public void executeWithTrace(String name, Runnable runnable) {
        brave.Span span = newSpan(name);
        try {
            span.start();
            runnable.run();
        } finally {
            span.finish();
        }
    }

    /**
     * 执行带追踪的操作（带返回值）
     *
     * @param name    Span 名称
     * @param supplier 操作
     * @param <T>     返回类型
     * @return 结果
     */
    public <T> T executeWithTrace(String name, java.util.function.Supplier<T> supplier) {
        brave.Span span = newSpan(name);
        try {
            span.start();
            return supplier.get();
        } finally {
            span.finish();
        }
    }

    /**
     * 记录错误
     *
     * @param error 错误
     */
    public void recordError(Throwable error) {
        if (tracer != null && tracer.currentSpan() != null) {
            tracer.currentSpan().error(error);
        }
    }

    /**
     * 记录标签
     *
     * @param key   键
     * @param value 值
     */
    public void recordTag(String key, String value) {
        if (tracer != null && tracer.currentSpan() != null) {
            tracer.currentSpan().tag(key, value);
        }
    }

    /**
     * 设置自定义上下文数据
     *
     * @param key   键
     * @param value 值
     */
    public void setContext(String key, Object value) {
        customContext.put(key, value);
    }

    /**
     * 获取自定义上下文数据
     *
     * @param key 键
     * @return 值
     */
    public Object getContext(String key) {
        return customContext.get(key);
    }

    /**
     * 清理上下文
     */
    public void clear() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SPAN_ID_KEY);
        MDC.remove(PARENT_SPAN_ID_KEY);
        customContext.clear();
    }

    /**
     * 检查是否启用追踪
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return tracer != null && tracer.currentSpan() != null;
    }

    /**
     * 获取当前 Span
     *
     * @return 当前 Span
     */
    public brave.Span getCurrentSpan() {
        return tracer != null ? tracer.currentSpan() : null;
    }

    /**
     * 获取 Tracer
     *
     * @return Tracer
     */
    public Tracer getTracer() {
        return tracer;
    }
}
