package com.yiyundao.compensation.common.exception;

import com.yiyundao.compensation.common.response.ErrorCode;
import lombok.Getter;

import java.util.Map;

/**
 * 业务异常
 * <p>
 * 用于表示业务逻辑处理中的错误情况。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 扩展数据
     */
    private final Map<String, Object> details;

    /**
     * 错误码枚举
     */
    private final ErrorCode errorCode;

    /**
     * 使用错误码枚举创建异常
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.errorCode = errorCode;
        this.details = null;
    }

    /**
     * 使用错误码枚举和自定义消息创建异常
     *
     * @param errorCode 错误码枚举
     * @param message   自定义消息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.message = message;
        this.errorCode = errorCode;
        this.details = null;
    }

    /**
     * 使用错误码枚举和扩展数据创建异常
     *
     * @param errorCode 错误码枚举
     * @param details   扩展数据
     */
    public BusinessException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.errorCode = errorCode;
        this.details = details;
    }

    /**
     * 使用错误码和消息创建异常
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
        this.errorCode = ErrorCode.fromCode(code);
        this.details = null;
    }

    /**
     * 使用错误码、消息和扩展数据创建异常
     *
     * @param code    错误码
     * @param message 错误消息
     * @param details 扩展数据
     */
    public BusinessException(Integer code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.message = message;
        this.errorCode = ErrorCode.fromCode(code);
        this.details = details;
    }

    /**
     * 使用错误码、消息和Cause创建异常
     *
     * @param code    错误码
     * @param message 错误消息
     * @param cause   原始异常
     */
    public BusinessException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
        this.errorCode = ErrorCode.fromCode(code);
        this.details = null;
    }

    /**
     * 使用错误码枚举和Cause创建异常
     *
     * @param errorCode 错误码枚举
     * @param cause     原始异常
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.errorCode = errorCode;
        this.details = null;
    }

    /**
     * 使用字符串消息创建异常（使用默认业务错误码）
     *
     * @param message 错误消息
     */
    public BusinessException(String message) {
        super(message);
        this.code = ErrorCode.BUSINESS_ERROR.getCode();
        this.message = message;
        this.errorCode = ErrorCode.BUSINESS_ERROR;
        this.details = null;
    }

    /**
     * 获取错误码对应的 HTTP 状态码
     *
     * @return HTTP 状态码
     */
    public int getHttpStatus() {
        if (errorCode != null) {
            return errorCode.getHttpStatusCode();
        }
        return 400;
    }

    /**
     * 检查是否为客户端错误
     *
     * @return 是否为客户端错误
     */
    public boolean isClientError() {
        if (errorCode != null) {
            return errorCode.isClientError();
        }
        if (code == null) {
            return false;
        }
        org.springframework.http.HttpStatus status = org.springframework.http.HttpStatus.resolve(code);
        return status != null && status.is4xxClientError();
    }

    /**
     * 检查是否为认证错误
     *
     * @return 是否为认证错误
     */
    public boolean isAuthenticationError() {
        if (errorCode != null) {
            return errorCode.isAuthenticationError();
        }
        return code != null && code == 401;
    }

    /**
     * 检查是否为权限错误
     *
     * @return 是否为权限错误
     */
    public boolean isAuthorizationError() {
        if (errorCode != null) {
            return errorCode.isAuthorizationError();
        }
        return code != null && code == 403;
    }
}
