package com.yiyundao.compensation.common.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 错误码定义
 * <p>
 * 统一管理系统的错误码，包含：
 * - 业务错误码（1xxx）
 * - 参数错误码（4xxx）
 * - 认证错误码（401xx）
 * - 权限错误码（403xx）
 * - 系统错误码（5xxx）
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Getter
public enum ErrorCode {

    // ==================== 成功 ====================

    /**
     * 成功
     */
    SUCCESS(0, "操作成功", HttpStatus.OK),

    // ==================== 业务错误码（1xxx） ====================

    /**
     * 业务处理失败
     */
    BUSINESS_ERROR(1001, "业务处理失败", HttpStatus.BAD_REQUEST),

    /**
     * 资源不存在
     */
    RESOURCE_NOT_FOUND(1002, "资源不存在", HttpStatus.NOT_FOUND),

    /**
     * 资源已存在
     */
    RESOURCE_EXISTS(1003, "资源已存在", HttpStatus.CONFLICT),

    /**
     * 业务状态不合法
     */
    INVALID_STATUS(1004, "业务状态不合法", HttpStatus.BAD_REQUEST),

    /**
     * 数据验证失败
     */
    VALIDATION_FAILED(1005, "数据验证失败", HttpStatus.BAD_REQUEST),

    // ==================== 参数错误码（4xxx） ====================

    /**
     * 参数缺失
     */
    PARAM_MISSING(4001, "参数缺失", HttpStatus.BAD_REQUEST),

    /**
     * 参数格式错误
     */
    PARAM_FORMAT_ERROR(4002, "参数格式错误", HttpStatus.BAD_REQUEST),

    /**
     * 参数值无效
     */
    PARAM_INVALID(4003, "参数值无效", HttpStatus.BAD_REQUEST),

    /**
     * 请求体解析失败
     */
    BODY_PARSE_ERROR(4004, "请求体解析失败", HttpStatus.BAD_REQUEST),

    /**
     * 请求类型不支持
     */
    UNSUPPORTED_MEDIA_TYPE(4005, "请求类型不支持", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    /**
     * 请求方法不支持
     */
    METHOD_NOT_ALLOWED(40501, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),

    /**
     * 请求冲突（如幂等冲突、资源版本冲突）
     */
    REQUEST_CONFLICT(40901, "请求冲突", HttpStatus.CONFLICT),

    /**
     * 请求过于频繁
     */
    TOO_MANY_REQUESTS(42901, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),

    // ==================== 认证错误码（401xx） ====================

    /**
     * 未登录或 Token 已过期
     */
    UNAUTHORIZED(40101, "未登录或 Token 已过期", HttpStatus.UNAUTHORIZED),

    /**
     * Token 无效
     */
    TOKEN_INVALID(40102, "Token 无效", HttpStatus.UNAUTHORIZED),

    /**
     * Token 格式错误
     */
    TOKEN_FORMAT_ERROR(40103, "Token 格式错误", HttpStatus.UNAUTHORIZED),

    /**
     * Token 已黑名单
     */
    TOKEN_BLACKLISTED(40104, "Token 已在黑名单中", HttpStatus.UNAUTHORIZED),

    /**
     * Refresh Token 无效
     */
    REFRESH_TOKEN_INVALID(40105, "Refresh Token 无效", HttpStatus.UNAUTHORIZED),

    // ==================== 权限错误码（403xx） ====================

    /**
     * 无权限访问
     */
    FORBIDDEN(40301, "无权限访问", HttpStatus.FORBIDDEN),

    /**
     * 角色权限不足
     */
    ROLE_INSUFFICIENT(40302, "角色权限不足", HttpStatus.FORBIDDEN),

    /**
     * 资源访问被拒绝
     */
    ACCESS_DENIED(40303, "资源访问被拒绝", HttpStatus.FORBIDDEN),

    // ==================== 系统错误码（5xxx） ====================

    /**
     * 系统内部错误
     */
    SYSTEM_ERROR(50001, "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR),

    /**
     * 数据库错误
     */
    DATABASE_ERROR(50002, "数据库错误", HttpStatus.INTERNAL_SERVER_ERROR),

    /**
     * 缓存服务异常
     */
    CACHE_ERROR(50003, "缓存服务异常", HttpStatus.INTERNAL_SERVER_ERROR),

    /**
     * 第三方服务异常
     */
    THIRD_PARTY_ERROR(50004, "第三方服务异常", HttpStatus.BAD_GATEWAY),

    /**
     * 请求超时
     */
    TIMEOUT_ERROR(50005, "请求超时", HttpStatus.GATEWAY_TIMEOUT),

    /**
     * 服务不可用
     */
    SERVICE_UNAVAILABLE(50006, "服务不可用", HttpStatus.SERVICE_UNAVAILABLE),

    // ==================== 外部集成错误码（6xxx） ====================

    /**
     * 支付宝支付失败
     */
    ALIPAY_PAYMENT_FAILED(60001, "支付宝支付失败", HttpStatus.BAD_REQUEST),

    /**
     * 企业微信 API 调用失败
     */
    WECHAT_API_ERROR(60002, "企业微信 API 调用失败", HttpStatus.BAD_GATEWAY),

    /**
     * 钉钉 API 调用失败
     */
    DINGTALK_API_ERROR(60003, "钉钉 API 调用失败", HttpStatus.BAD_GATEWAY),

    /**
     * 飞书 API 调用失败
     */
    FEISHU_API_ERROR(60004, "飞书 API 调用失败", HttpStatus.BAD_GATEWAY);

    // ==================== 属性 ====================

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * HTTP 状态码
     */
    private final HttpStatus httpStatus;

    // ==================== 构造函数 ====================

    ErrorCode(Integer code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    // ==================== 辅助方法 ====================

    /**
     * 通过错误码查找枚举
     *
     * @param code 错误码
     * @return ErrorCode（未找到返回 null）
     */
    public static ErrorCode fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return null;
    }

    /**
     * 获取对应的 HTTP 状态码
     *
     * @return HTTP 状态码
     */
    public int getHttpStatusCode() {
        return httpStatus.value();
    }

    /**
     * 是否为客户端错误（4xx）
     *
     * @return 是否为客户端错误
     */
    public boolean isClientError() {
        return httpStatus.is4xxClientError();
    }

    /**
     * 是否为服务端错误（5xx）
     *
     * @return 是否为服务端错误
     */
    public boolean isServerError() {
        return httpStatus.is5xxServerError();
    }

    /**
     * 是否为认证错误
     *
     * @return 是否为认证错误
     */
    public boolean isAuthenticationError() {
        return httpStatus == HttpStatus.UNAUTHORIZED;
    }

    /**
     * 是否为权限错误
     *
     * @return 是否为权限错误
     */
    public boolean isAuthorizationError() {
        return httpStatus == HttpStatus.FORBIDDEN;
    }
}
