package com.yiyundao.compensation.common.metrics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * API 指标采集切面
 * <p>
 * 自动采集所有 Controller 方法的：
 * - 请求次数
 * - 响应时间
 * - 异常次数
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
@Aspect
@Component
public class ApiMetricsAspect {

    private final MetricsService metricsService;

    public ApiMetricsAspect(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * 定义切入点：所有 Controller 方法
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) || " +
              "within(@org.springframework.stereotype.Controller *)")
    public void controllerPointcut() {
    }

    /**
     * 环绕通知：采集 API 指标
     */
    @Around("controllerPointcut()")
    public Object aroundController(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取请求信息
        ServletRequestAttributes attributes = getRequestAttributes();
        String uri = "/unknown";
        String method = "UNKNOWN";
        int status = 200;

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            uri = request.getRequestURI();
            method = request.getMethod();

            // 获取状态码（从响应中获取）
            HttpServletResponse response = attributes.getResponse();
            if (response != null) {
                status = response.getStatus();
            }
        }

        // 获取方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        // 构建标签
        Map<String, String> tags = new HashMap<>();
        tags.put("controller", className);
        tags.put("method", methodName);
        tags.put("uri", normalizeUri(uri));
        tags.put("http_method", method);

        try {
            // 执行方法
            Object result = joinPoint.proceed();

            // 记录成功响应
            status = getResponseStatus(attributes, status);

            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordApiRequest(uri, method, status, duration);

            return result;

        } catch (Throwable e) {
            // 记录异常
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordApiRequest(uri, method, 500, duration);
            metricsService.recordException(e.getClass().getSimpleName(), tags);

            throw e;
        }
    }

    /**
     * 获取响应状态码
     */
    private int getResponseStatus(ServletRequestAttributes attributes, int defaultStatus) {
        if (attributes == null) {
            return defaultStatus;
        }
        HttpServletResponse response = attributes.getResponse();
        if (response != null) {
            return response.getStatus();
        }
        return defaultStatus;
    }

    /**
     * 获取请求属性
     */
    private ServletRequestAttributes getRequestAttributes() {
        try {
            return (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 规范化 URI
     */
    private String normalizeUri(String uri) {
        if (!StringUtils.hasText(uri)) {
            return "unknown";
        }
        // 移除上下文路径
        String normalized = uri.replaceFirst("^/api", "");
        if (normalized.isEmpty()) {
            normalized = "/";
        }
        // 简化路径参数
        normalized = normalized.replaceAll("/\\d+", "/{id}");
        normalized = normalized.replaceAll("/[a-f0-9-]{36}", "/{uuid}");
        return normalized;
    }
}
