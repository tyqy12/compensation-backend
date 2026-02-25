package com.yiyundao.compensation.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页信息
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageInfo {

    /**
     * 当前页码（从 1 开始）
     */
    private int pageNum;

    /**
     * 每页大小
     */
    private int pageSize;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 是否有下一页
     */
    private boolean hasNext;

    /**
     * 是否有上一页
     */
    private boolean hasPrevious;

    /**
     * 计算辅助字段
     */
    public void calculateDerivedFields() {
        this.hasNext = this.pageNum < this.totalPages;
        this.hasPrevious = this.pageNum > 1;
    }
}
