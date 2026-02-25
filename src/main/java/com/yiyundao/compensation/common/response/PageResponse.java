package com.yiyundao.compensation.common.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分页响应包装类
 * <p>
 * 用于包装 MyBatis-Plus 的 IPage 对象，转换为统一的分页响应格式。
 * </p>
 *
 * @param <T> 分页数据类型
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页响应封装")
public class PageResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据列表
     */
    @Schema(description = "数据列表")
    private List<T> list;

    /**
     * 当前页码（从 1 开始）
     */
    @Schema(description = "当前页码")
    private Integer pageNum;

    /**
     * 每页大小
     */
    @Schema(description = "每页大小")
    private Integer pageSize;

    /**
     * 总记录数
     */
    @Schema(description = "总记录数")
    private Long total;

    /**
     * 总页数
     */
    @Schema(description = "总页数")
    private Integer totalPages;

    /**
     * 是否有下一页
     */
    @Schema(description = "是否有下一页")
    private Boolean hasNextPage;

    /**
     * 是否有上一页
     */
    @Schema(description = "是否有上一页")
    private Boolean hasPreviousPage;

    // ==================== 静态工厂方法 ====================

    /**
     * 从 MyBatis-Plus IPage 构建分页响应
     *
     * @param page    MyBatis-Plus 分页对象
     * @param records 转换后的记录列表
     * @param <T>     原始数据类型
     * @param <R>     目标数据类型
     * @return PageResponse
     */
    public static <T, R> PageResponse<R> of(IPage<T> page, List<R> records) {
        int pageNum = (int) page.getCurrent();
        int pageSize = (int) page.getSize();
        long total = page.getTotal();
        int totalPages = (int) Math.ceil((double) total / pageSize);

        return PageResponse.<R>builder()
                .list(records)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .total(total)
                .totalPages(totalPages)
                .hasNextPage(pageNum < totalPages)
                .hasPreviousPage(pageNum > 1)
                .build();
    }

    /**
     * 从 MyBatis-Plus IPage 构建分页响应（自动转换记录类型）
     *
     * @param page     MyBatis-Plus 分页对象
     * @param converter 记录转换器
     * @param <T>      原始数据类型
     * @param <R>      目标数据类型
     * @return PageResponse
     */
    public static <T, R> PageResponse<R> of(IPage<T> page, java.util.function.Function<T, R> converter) {
        return of(page, page.getRecords().stream()
                .map(converter)
                .collect(Collectors.toList()));
    }

    /**
     * 从 MyBatis-Plus IPage 构建分页响应（记录类型不变）
     *
     * @param page MyBatis-Plus 分页对象
     * @param <T>  数据类型
     * @return PageResponse
     */
    public static <T> PageResponse<T> of(IPage<T> page) {
        return of(page, page.getRecords());
    }

    /**
     * 手动构建分页响应
     *
     * @param list     数据列表
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @param total    总记录数
     * @param <T>      数据类型
     * @return PageResponse
     */
    public static <T> PageResponse<T> of(List<T> list, int pageNum, int pageSize, long total) {
        int totalPages = (int) Math.ceil((double) total / pageSize);

        return PageResponse.<T>builder()
                .list(list)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .total(total)
                .totalPages(totalPages)
                .hasNextPage(pageNum < totalPages)
                .hasPreviousPage(pageNum > 1)
                .build();
    }

    /**
     * 兼容旧版本：使用 Long 类型
     *
     * @param list    数据列表
     * @param current 当前页码
     * @param size    每页大小
     * @param total   总记录数
     * @param <T>     数据类型
     * @return PageResponse
     */
    public static <T> PageResponse<T> of(List<T> list, Long current, Long size, Long total) {
        return of(list, current.intValue(), size.intValue(), total);
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取分页偏移量
     *
     * @return 偏移量
     */
    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }

    /**
     * 创建 ApiResponse 分页成功响应
     *
     * @return ApiResponse<PageResponse<T>>
     */
    public ApiResponse<PageResponse<T>> toApiResponse() {
        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .total(total)
                .totalPages(totalPages)
                .build();
        return ApiResponse.<PageResponse<T>>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(this)
                .page(pageInfo)
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    /**
     * 创建 ApiResponse 分页成功响应（自定义消息）
     *
     * @param message 成功消息
     * @return ApiResponse<PageResponse<T>>
     */
    public ApiResponse<PageResponse<T>> toApiResponse(String message) {
        return ApiResponse.<PageResponse<T>>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(message)
                .data(this)
                .page(ApiResponse.PageInfo.builder()
                        .pageNum(pageNum)
                        .pageSize(pageSize)
                        .total(total)
                        .totalPages(totalPages)
                        .build())
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }
}
