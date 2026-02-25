package com.yiyundao.compensation.common.handler;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.UUID;

/**
 * 全局响应处理器
 * <p>
 * 功能：
 * 1. 统一包装响应格式
 * 2. 自动添加链路追踪 ID
 * 3. 自动添加时间戳
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
@ControllerAdvice
public class GlobalResponseHandler implements ResponseBodyAdvice<Object> {

    /**
     * 链路追踪 ID 键
     */
    public static final String TRACE_ID_KEY = "traceId";

    /**
     * 不需要包装的响应类型
     */
    private static final Class<?>[] EXCLUDED_TYPES = {
            String.class,                // 字符串（如 HTML）
            byte[].class,                // 二进制数据
            HttpEntity.class,            // ResponseEntity / HttpEntity（文件下载、手动控制 status/header 等）
    };

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 检查返回类型是否需要包装
        Class<?> returnClass = returnType.getParameterType();
        for (Class<?> excludedType : EXCLUDED_TYPES) {
            if (excludedType.isAssignableFrom(returnClass)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {

        // 1. 生成或获取 traceId
        String traceId = getOrCreateTraceId();

        // 2. 若已是 ApiResponse：补齐通用字段 + 统一 HTTP status 映射
        if (body instanceof ApiResponse) {
            ApiResponse<?> apiResponse = (ApiResponse<?>) body;
            if (apiResponse.getTraceId() == null || apiResponse.getTraceId().isBlank()) {
                apiResponse.setTraceId(traceId);
            }
            if (apiResponse.getTimestamp() == null) {
                apiResponse.setTimestamp(java.time.LocalDateTime.now());
            }

            HttpStatus httpStatus = resolveHttpStatus(apiResponse.getCode());
            if (httpStatus != null) {
                response.setStatusCode(httpStatus);
            }
            return apiResponse;
        }

        // 3. 构建新的响应对象（成功）
        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(body)
                .traceId(traceId)
                .timestamp(java.time.LocalDateTime.now())
                .build();

        response.setStatusCode(HttpStatus.OK);

        log.debug("响应包装: traceId={}, uri={}, method={}",
                traceId,
                request.getURI().getPath(),
                request.getMethod());

        return apiResponse;
    }

    private HttpStatus resolveHttpStatus(Integer code) {
        if (code == null) {
            return HttpStatus.OK;
        }
        if (ErrorCode.SUCCESS.getCode().equals(code)) {
            return HttpStatus.OK;
        }

        ErrorCode errorCode = ErrorCode.fromCode(code);
        if (errorCode != null) {
            return errorCode.getHttpStatus();
        }

        // 兼容：历史上把 HTTP status 写进 ApiResponse.code
        HttpStatus legacyHttpStatus = HttpStatus.resolve(code);
        if (legacyHttpStatus != null) {
            return legacyHttpStatus;
        }

        // 兜底：无法识别的非 0 code，按 4xx/5xx 粗粒度推断
        return code >= 50000 ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
    }

    /**
     * 获取或创建链路追踪 ID
     *
     * @return traceId
     */
    private String getOrCreateTraceId() {
        // 1. 尝试从 MDC 获取
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId != null && !traceId.isEmpty()) {
            return traceId;
        }

        // 2. 尝试从请求属性获取
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                Object requestTraceId = request.getAttribute(TRACE_ID_KEY);
                if (requestTraceId instanceof String) {
                    traceId = (String) requestTraceId;
                    MDC.put(TRACE_ID_KEY, traceId);
                    return traceId;
                }
            }
        } catch (Exception e) {
            log.debug("获取请求属性失败: {}", e.getMessage());
        }

        // 3. 生成新的 traceId
        traceId = generateTraceId();
        MDC.put(TRACE_ID_KEY, traceId);

        // 4. 设置到请求属性（供后续使用）
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                attributes.getRequest().setAttribute(TRACE_ID_KEY, traceId);
            }
        } catch (Exception e) {
            log.debug("设置请求属性失败: {}", e.getMessage());
        }

        return traceId;
    }

    /**
     * 生成链路追踪 ID
     *
     * @return traceId
     */
    private String generateTraceId() {
        // 使用 UUID 的前 16 位作为 traceId
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, 16).toLowerCase();
    }

    /**
     * 获取当前请求的 traceId
     *
     * @return traceId（如果存在）
     */
    public static String getCurrentTraceId() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                Object traceId = attributes.getRequest().getAttribute(TRACE_ID_KEY);
                if (traceId instanceof String) {
                    return (String) traceId;
                }
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return MDC.get(TRACE_ID_KEY);
    }
}
