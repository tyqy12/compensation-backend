package com.yiyundao.compensation.modules.audit.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志查询请求
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
@Data
public class AuditLogQueryRequest {

    /**
     * 当前页码
     */
    private int page = 1;

    /**
     * 每页大小
     */
    private int size = 20;

    /**
     * 用户名
     */
    private String username;

    /**
     * 操作类型
     */
    private String operation;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 请求IP
     */
    private String requestIp;

    /**
     * 响应结果 (OK/FAILED)
     */
    private String responseResult;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 搜索关键词
     */
    private String keyword;
}
