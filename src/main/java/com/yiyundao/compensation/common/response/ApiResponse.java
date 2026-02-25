package com.yiyundao.compensation.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统一 API 响应封装
 * <p>
 * 适用于所有 REST API 的响应格式，包含：
 * - 状态码和消息
 * - 业务数据
 * - 链路追踪 ID
 * - 时间戳
 * - 分页信息
 * </p>
 *
 * @param <T> 响应数据类型
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * 状态码
     * 0: 成功
     * 其他: 错误码
     */
    private Integer code;

    /**
     * 状态消息
     */
    private String message;

    /**
     * 业务数据
     */
    private T data;

    /**
     * 链路追踪 ID（用于问题排查）
     */
    private String traceId;

    /**
     * 响应时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 分页信息（仅分页接口有值）
     */
    private PageInfo page;

    /**
     * 扩展信息（可选）
     */
    private Map<String, Object> extra;

    // ==================== 静态工厂方法 ====================

    /**
     * 成功响应（无数据）
     *
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    /**
     * 成功响应（带数据）
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 成功响应（带数据和分页信息）
     *
     * @param data     业务数据
     * @param pageInfo 分页信息
     * @param <T>      数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> success(T data, PageInfo pageInfo) {
        return ApiResponse.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .page(pageInfo)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 成功响应（带数据和扩展信息）
     *
     * @param data 业务数据
     * @param extra 扩展信息
     * @param <T> 数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> success(T data, Map<String, Object> extra) {
        return ApiResponse.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .extra(extra)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 错误响应
     *
     * @param code    错误码
     * @param message 错误消息
     * @param <T>     数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 错误响应（使用 ErrorCode）
     *
     * @param errorCode 错误码枚举
     * @param <T>       数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return ApiResponse.<T>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 成功响应（带数据和消息）
     *
     * @param message 成功消息
     * @param data    业务数据
     * @param <T>     数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 错误响应（使用默认错误码）
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .code(ErrorCode.BUSINESS_ERROR.getCode())
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 错误响应（使用 ErrorCode，带自定义消息）
     *
     * @param errorCode 错误码枚举
     * @param message   自定义消息（会覆盖默认消息）
     * @param <T>       数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return ApiResponse.<T>builder()
                .code(errorCode.getCode())
                .message(message != null ? message : errorCode.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 错误响应（带扩展信息）
     *
     * @param code    错误码
     * @param message 错误消息
     * @param extra   扩展信息
     * @param <T>     数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<T> error(Integer code, String message, Map<String, Object> extra) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .extra(extra)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 分页成功响应
     *
     * @param list     数据列表
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @param total    总记录数
     * @param <T>      数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<List<T>> page(List<T> list, int pageNum, int pageSize, long total) {
        PageInfo pageInfo = PageInfo.builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .total(total)
                .totalPages((int) Math.ceil((double) total / pageSize))
                .build();
        return ApiResponse.<List<T>>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(list)
                .page(pageInfo)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 分页成功响应（使用 PageInfo）
     *
     * @param list     数据列表
     * @param pageInfo 分页信息
     * @param <T>      数据类型
     * @return ApiResponse
     */
    public static <T> ApiResponse<List<T>> page(List<T> list, PageInfo pageInfo) {
        return ApiResponse.<List<T>>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(list)
                .page(pageInfo)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 判断是否成功
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return ErrorCode.SUCCESS.getCode().equals(this.code);
    }

    /**
     * 获取数据（安全转换类型）
     * <p>
     * 如果类型不匹配或数据为 null，返回 null 而不抛出异常。
     * </p>
     *
     * @param targetType 目标类型
     * @return 转换后的数据，类型不匹配时返回 null
     */
    @SuppressWarnings("unchecked")
    public <R> R getDataAs(Class<R> targetType) {
        if (this.data == null) {
            return null;
        }
        if (targetType.isInstance(this.data)) {
            return (R) this.data;
        }
        // 类型不匹配时返回 null，而非抛出异常
        return null;
    }

    // ==================== 内部类 ====================

    /**
     * 分页信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageInfo {
        /**
         * 当前页码（从 1 开始）
         */
        private Integer pageNum;

        /**
         * 每页大小
         */
        private Integer pageSize;

        /**
         * 总记录数
         */
        private Long total;

        /**
         * 总页数
         */
        private Integer totalPages;

        /**
         * 是否有下一页
         */
        private Boolean hasNextPage;

        /**
         * 是否有上一页
         */
        private Boolean hasPreviousPage;

        /**
         * 创建分页信息
         *
         * @param pageNum  当前页码
         * @param pageSize 每页大小
         * @param total    总记录数
         * @return PageInfo
         */
        public static PageInfo of(int pageNum, int pageSize, long total) {
            int totalPages = (int) Math.ceil((double) total / pageSize);
            return PageInfo.builder()
                    .pageNum(pageNum)
                    .pageSize(pageSize)
                    .total(total)
                    .totalPages(totalPages)
                    .hasNextPage(pageNum < totalPages)
                    .hasPreviousPage(pageNum > 1)
                    .build();
        }

        /**
         * 计算偏移量
         *
         * @return 偏移量
         */
        public int getOffset() {
            return (pageNum - 1) * pageSize;
        }
    }
}
