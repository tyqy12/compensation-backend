package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import com.yiyundao.compensation.modules.audit.listener.AuditMetricsListener;
import com.yiyundao.compensation.modules.audit.listener.LoginFailureListener;
import com.yiyundao.compensation.modules.audit.service.AuditLogQueryService;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import com.yiyundao.compensation.security.SecurityAnnotations;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/audit-logs")
@RequiredArgsConstructor
@SecurityAnnotations.IsAdmin
public class AuditLogAdminController {

    private final AuditLogService auditLogService;
    private final AuditLogQueryService auditLogQueryService;
    private final AuditMetricsListener auditMetricsListener;
    private final LoginFailureListener loginFailureListener;

    /**
     * 分页查询审计日志
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> page(@RequestParam(defaultValue = "1") int current,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 @RequestParam(required = false) String username,
                                                 @RequestParam(required = false) String operation,
                                                 @RequestParam(required = false) String businessType,
                                                 @RequestParam(required = false) String businessKey,
                                                 @RequestParam(required = false) String responseResult,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                                 @RequestParam(required = false) String keyword) {
        Page<AuditLog> page = new Page<>(current, pageSize);
        LambdaQueryWrapper<AuditLog> w = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) w.like(AuditLog::getUsername, username);
        if (operation != null && !operation.isBlank()) w.like(AuditLog::getOperation, operation);
        if (businessType != null && !businessType.isBlank()) w.eq(AuditLog::getBusinessType, businessType);
        if (businessKey != null && !businessKey.isBlank()) w.eq(AuditLog::getBusinessKey, businessKey);
        if (responseResult != null && !responseResult.isBlank()) w.eq(AuditLog::getResponseResult, responseResult);
        if (startTime != null) w.ge(AuditLog::getCreateTime, startTime);
        if (endTime != null) w.le(AuditLog::getCreateTime, endTime);
        if (keyword != null && !keyword.isBlank()) {
            w.and(wrapper -> wrapper
                    .like(AuditLog::getOperation, keyword)
                    .or()
                    .like(AuditLog::getUsername, keyword)
                    .or()
                    .like(AuditLog::getErrorMsg, keyword)
            );
        }
        w.orderByDesc(AuditLog::getCreateTime);
        Page<AuditLog> result = auditLogService.page(page, w);
        Map<String, Object> resp = new HashMap<>();
        resp.put("records", result.getRecords());
        resp.put("total", result.getTotal());
        resp.put("current", result.getCurrent());
        resp.put("size", result.getSize());
        return ApiResponse.success(resp);
    }

    /**
     * 查询审计日志详情
     */
    @GetMapping("/{id}")
    public ApiResponse<AuditLog> detail(@PathVariable Long id) {
        return ApiResponse.success(auditLogService.getById(id));
    }

    /**
     * 查询用户最近操作记录
     */
    @GetMapping("/user/{username}/recent")
    public ApiResponse<List<AuditLog>> getUserRecentOperations(
            @PathVariable String username,
            @RequestParam(defaultValue = "10") int limit) {
        List<AuditLog> records = auditLogQueryService.findRecentByUsername(username, Math.min(limit, 50));
        return ApiResponse.success(records);
    }

    /**
     * 查询指定时间范围内的操作记录
     */
    @GetMapping("/time-range")
    public ApiResponse<List<AuditLog>> getByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        List<AuditLog> records = auditLogQueryService.findByTimeRange(startTime, endTime);
        return ApiResponse.success(records);
    }

    /**
     * 获取今日登录统计
     */
    @GetMapping("/stats/today-login")
    public ApiResponse<Map<String, Object>> getTodayLoginStats() {
        Map<String, Object> stats = auditLogQueryService.getTodayLoginStats();
        return ApiResponse.success(stats);
    }

    /**
     * 获取今日统计摘要
     */
    @GetMapping("/stats/summary")
    public ApiResponse<Map<String, Object>> getTodaySummary() {
        Map<String, Object> summary = auditMetricsListener.getTodaySummary();
        return ApiResponse.success(summary);
    }

    /**
     * 获取操作类型统计
     */
    @GetMapping("/stats/operations")
    public ApiResponse<List<Map<String, Object>>> getOperationStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        List<Map<String, Object>> stats = auditLogQueryService.getOperationStats(startTime, endTime);
        return ApiResponse.success(stats);
    }

    /**
     * 获取登录失败计数（监控用）
     */
    @GetMapping("/security/login-failures")
    public ApiResponse<Map<String, Integer>> getLoginFailureCount() {
        Map<String, Integer> failureCount = loginFailureListener.getLoginFailureCount();
        return ApiResponse.success(failureCount);
    }

    /**
     * 清除指定用户的登录失败计数
     */
    @DeleteMapping("/security/login-failures/{username}")
    public ApiResponse<Void> clearLoginFailureCount(@PathVariable String username) {
        loginFailureListener.clearFailureCount(username);
        return ApiResponse.success(null);
    }

    /**
     * 获取审计指标统计（实时）
     */
    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = auditMetricsListener.getTodaySummary();
        return ApiResponse.success(metrics);
    }
}
