package com.yiyundao.compensation.common.exception;

import com.yiyundao.compensation.common.handler.GlobalResponseHandler;
import com.yiyundao.compensation.common.metrics.MetricsService;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.trace.TraceContext;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 统一处理系统中的各类异常，返回统一的错误响应格式。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MetricsService metricsService;
    private final TraceContext traceContext;
    private final Environment environment;

    @Autowired
    public GlobalExceptionHandler(MetricsService metricsService,
                                  @Autowired(required = false) TraceContext traceContext,
                                  @Autowired(required = false) Environment environment) {
        this.metricsService = metricsService;
        this.traceContext = traceContext;
        this.environment = environment;
    }

    /**
     * 业务异常处理
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        String traceId = getTraceId();
        log.warn("业务异常: traceId={}, code={}, message={}", traceId, ex.getCode(), ex.getMessage());

        // 记录异常指标
        recordException("BusinessException");

        ErrorCode errorCode = resolveBusinessErrorCode(ex);

        Map<String, Object> extra = new HashMap<>();
        extra.put("traceId", traceId);
        if (ex.getDetails() != null) {
            extra.put("details", ex.getDetails());
        }

        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(buildErrorBody(errorCode, ex.getMessage(), traceId, extra));
    }

    /**
     * 参数校验异常处理
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception ex) {
        String traceId = getTraceId();

        String message;
        if (ex instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException validEx = (MethodArgumentNotValidException) ex;
            message = validEx.getBindingResult().getFieldErrors().stream()
                    .map(e -> e.getField() + ": " + e.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        } else {
            BindException bindEx = (BindException) ex;
            message = bindEx.getFieldErrors().stream()
                    .map(e -> e.getField() + ": " + e.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }

        log.warn("参数校验失败: traceId={}, message={}", traceId, message);

        Map<String, Object> extra = new HashMap<>();
        extra.put("traceId", traceId);
        extra.put("errorType", "validation");

        return ResponseEntity.status(ErrorCode.PARAM_INVALID.getHttpStatus())
                .body(buildErrorBody(ErrorCode.PARAM_INVALID, message, traceId, extra));
    }

    /**
     * 约束校验异常处理
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String traceId = getTraceId();

        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));

        log.warn("约束校验失败: traceId={}, message={}", traceId, message);

        Map<String, Object> extra = new HashMap<>();
        extra.put("traceId", traceId);
        extra.put("errorType", "constraintViolation");

        return ResponseEntity.status(ErrorCode.PARAM_INVALID.getHttpStatus())
                .body(buildErrorBody(ErrorCode.PARAM_INVALID, message, traceId, extra));
    }

    /**
     * 请求参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String traceId = getTraceId();
        String message = String.format("参数 '%s' 类型不匹配，期望类型: %s",
                ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        log.warn("参数类型不匹配: traceId={}, message={}", traceId, message);

        Map<String, Object> extra = new HashMap<>();
        extra.put("traceId", traceId);
        extra.put("param", ex.getName());

        return ResponseEntity.status(ErrorCode.PARAM_FORMAT_ERROR.getHttpStatus())
                .body(buildErrorBody(ErrorCode.PARAM_FORMAT_ERROR, message, traceId, extra));
    }

    /**
     * 请求体解析异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String traceId = getTraceId();
        log.warn("请求体解析失败: traceId={}, message={}", traceId, ex.getMessage());

        return ResponseEntity.status(ErrorCode.BODY_PARSE_ERROR.getHttpStatus())
                .body(buildErrorBody(ErrorCode.BODY_PARSE_ERROR, "请求体格式错误", traceId, Map.of("traceId", traceId)));
    }

    /**
     * 不支持的请求方法
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String traceId = getTraceId();
        String message = String.format("不支持的请求方法: %s", ex.getMethod());

        log.warn("不支持的请求方法: traceId={}, message={}", traceId, message);

        Map<String, Object> extra = new HashMap<>();
        extra.put("traceId", traceId);
        extra.put("method", ex.getMethod());

        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(buildErrorBody(ErrorCode.METHOD_NOT_ALLOWED, message, traceId, extra));
    }

    /**
     * 不支持的媒体类型
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        String traceId = getTraceId();
        String message = String.format("不支持的媒体类型: %s", ex.getContentType());

        log.warn("不支持的媒体类型: traceId={}, message={}", traceId, message);

        return ResponseEntity.status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getHttpStatus())
                .body(buildErrorBody(ErrorCode.UNSUPPORTED_MEDIA_TYPE, message, traceId, Map.of("traceId", traceId)));
    }

    /**
     * 资源不存在异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoHandlerFoundException ex) {
        String traceId = getTraceId();
        String message = String.format("接口不存在: %s %s", ex.getHttpMethod(), ex.getRequestURL());

        log.warn("接口不存在: traceId={}, message={}", traceId, message);

        Map<String, Object> extra = new HashMap<>();
        extra.put("traceId", traceId);
        extra.put("path", ex.getRequestURL());

        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(buildErrorBody(ErrorCode.RESOURCE_NOT_FOUND, message, traceId, extra));
    }

    /**
     * 认证异常
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        String traceId = getTraceId();
        log.warn("认证失败: traceId={}, message={}", traceId, ex.getMessage());

        recordException("AuthenticationException");

        String message = "认证失败";
        if (ex.getMessage() != null && ex.getMessage().contains("Token")) {
            message = "Token 无效或已过期";
        }

        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getHttpStatus())
                .body(buildErrorBody(ErrorCode.UNAUTHORIZED, message, traceId, Map.of("traceId", traceId)));
    }

    /**
     * 权限不足异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        String traceId = getTraceId();
        log.warn("权限不足: traceId={}, message={}", traceId, ex.getMessage());

        recordException("AccessDeniedException");

        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(buildErrorBody(ErrorCode.FORBIDDEN, "权限不足，无法访问该资源", traceId, Map.of("traceId", traceId)));
    }

    /**
     * 非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        String traceId = getTraceId();
        log.warn("非法参数: traceId={}, message={}", traceId, ex.getMessage());

        return ResponseEntity.status(ErrorCode.PARAM_INVALID.getHttpStatus())
                .body(buildErrorBody(ErrorCode.PARAM_INVALID, ex.getMessage(), traceId, Map.of("traceId", traceId)));
    }

    /**
     * 文件上传大小超限
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        String traceId = getTraceId();
        log.warn("文件上传大小超限: traceId={}, maxUploadSize={}", traceId, ex.getMaxUploadSize());

        return ResponseEntity.status(ErrorCode.PARAM_INVALID.getHttpStatus())
                .body(buildErrorBody(ErrorCode.PARAM_INVALID, "文件大小超过限制", traceId, Map.of("traceId", traceId)));
    }

    /**
     * Multipart 请求解析异常
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
        String traceId = getTraceId();
        log.warn("Multipart请求解析失败: traceId={}, message={}", traceId, ex.getMessage());

        return ResponseEntity.status(ErrorCode.PARAM_INVALID.getHttpStatus())
                .body(buildErrorBody(ErrorCode.PARAM_INVALID, "文件上传请求格式错误", traceId, Map.of("traceId", traceId)));
    }

    /**
     * 数据库异常
     */
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(org.springframework.dao.DataAccessException ex) {
        String traceId = getTraceId();
        log.error("数据库异常: traceId={}", traceId, ex);

        recordException("DataAccessException");

        return ResponseEntity.status(ErrorCode.DATABASE_ERROR.getHttpStatus())
                .body(buildErrorBody(ErrorCode.DATABASE_ERROR, "数据库操作失败", traceId, Map.of("traceId", traceId)));
    }

    /**
     * 其他系统异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        String traceId = getTraceId();
        log.error("系统异常: traceId={}", traceId, ex);

        recordException("Exception");

        // 生产环境不返回详细错误信息
        String message = isProductionEnv() ? "系统繁忙，请稍后重试" : ex.getMessage();

        return ResponseEntity.status(ErrorCode.SYSTEM_ERROR.getHttpStatus())
                .body(buildErrorBody(ErrorCode.SYSTEM_ERROR, message, traceId, Map.of("traceId", traceId)));
    }

    private ApiResponse<Void> buildErrorBody(ErrorCode errorCode,
                                            String message,
                                            String traceId,
                                            Map<String, Object> extra) {
        return ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(message != null ? message : errorCode.getMessage())
                .traceId(traceId)
                .timestamp(java.time.LocalDateTime.now())
                .extra(extra == null ? null : new HashMap<>(extra))
                .build();
    }

    private ErrorCode resolveBusinessErrorCode(BusinessException ex) {
        if (ex.getErrorCode() != null) {
            return ex.getErrorCode();
        }
        Integer rawCode = ex.getCode();
        ErrorCode fromEnum = ErrorCode.fromCode(rawCode);
        if (fromEnum != null) {
            return fromEnum;
        }
        if (rawCode == null) {
            return ErrorCode.BUSINESS_ERROR;
        }

        // 兼容：历史上直接传入 HTTP status（400/401/403/404/409/429/500）
        return switch (rawCode) {
            case 400 -> ErrorCode.BUSINESS_ERROR;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 409 -> ErrorCode.RESOURCE_EXISTS;
            case 429 -> ErrorCode.TOO_MANY_REQUESTS;
            case 500 -> ErrorCode.SYSTEM_ERROR;
            default -> rawCode >= 500 ? ErrorCode.SYSTEM_ERROR : ErrorCode.BUSINESS_ERROR;
        };
    }

    /**
     * 获取当前 TraceId
     */
    private String getTraceId() {
        String traceId = GlobalResponseHandler.getCurrentTraceId();
        if (traceId == null) {
            traceId = MDC.get("traceId");
        }
        if (traceId == null && traceContext != null) {
            traceId = traceContext.getTraceId();
        }
        return traceId != null ? traceId : "unknown";
    }

    /**
     * 记录异常指标
     */
    private void recordException(String exceptionType) {
        if (metricsService != null) {
            metricsService.recordException(exceptionType, Map.of());
        }
    }

    /**
     * 判断是否为生产环境
     */
    private boolean isProductionEnv() {
        if (environment != null) {
            if (containsProdLikeProfile(environment.getActiveProfiles())) {
                return true;
            }
            if (containsProdLikeProfile(environment.getDefaultProfiles())) {
                return true;
            }
        }
        return containsProdLikeProfile(System.getProperty("spring.profiles.active", ""));
    }

    private boolean containsProdLikeProfile(String... profiles) {
        if (profiles == null) {
            return false;
        }
        for (String profile : profiles) {
            if (profile == null) {
                continue;
            }
            String normalized = profile.trim().toLowerCase(java.util.Locale.ROOT);
            if ("prod".equals(normalized) || "production".equals(normalized) || "staging".equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
