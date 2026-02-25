package com.yiyundao.compensation.modules.audit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.audit.dto.AuditLogQueryRequest;
import com.yiyundao.compensation.modules.audit.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审计日志查询服务接口
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
public interface AuditLogQueryService extends IService<AuditLog> {

    /**
     * 分页查询审计日志
     *
     * @param request 查询条件
     * @return 分页结果
     */
    Map<String, Object> queryByPage(AuditLogQueryRequest request);

    /**
     * 查询用户最近操作记录
     *
     * @param username 用户名
     * @param limit    限制数量
     * @return 操作记录列表
     */
    List<AuditLog> findRecentByUsername(String username, int limit);

    /**
     * 查询指定时间范围内的操作记录
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 操作记录列表
     */
    List<AuditLog> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计用户登录失败次数
     *
     * @param username 用户名
     * @param minutes  时间窗口（分钟）
     * @return 失败次数
     */
    int countLoginFailures(String username, int minutes);

    /**
     * 获取今日登录统计
     *
     * @return 登录统计数据
     */
    Map<String, Object> getTodayLoginStats();

    /**
     * 获取操作类型统计
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 操作类型统计
     */
    List<Map<String, Object>> getOperationStats(LocalDateTime startTime, LocalDateTime endTime);
}
