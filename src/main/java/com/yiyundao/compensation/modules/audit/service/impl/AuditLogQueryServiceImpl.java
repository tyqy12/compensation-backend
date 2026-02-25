package com.yiyundao.compensation.modules.audit.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.AuditLogMapper;
import com.yiyundao.compensation.modules.audit.dto.AuditLogQueryRequest;
import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import com.yiyundao.compensation.modules.audit.service.AuditLogQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 审计日志查询服务实现
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogQueryServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements AuditLogQueryService {

    @Override
    public Map<String, Object> queryByPage(AuditLogQueryRequest request) {
        // 构建查询条件
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuditLog> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();

        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            wrapper.eq(AuditLog::getUsername, request.getUsername());
        }
        if (request.getOperation() != null && !request.getOperation().isEmpty()) {
            wrapper.like(AuditLog::getOperation, request.getOperation());
        }
        if (request.getBusinessType() != null && !request.getBusinessType().isEmpty()) {
            wrapper.eq(AuditLog::getBusinessType, request.getBusinessType());
        }
        if (request.getResponseResult() != null && !request.getResponseResult().isEmpty()) {
            wrapper.eq(AuditLog::getResponseResult, request.getResponseResult());
        }
        if (request.getRequestIp() != null && !request.getRequestIp().isEmpty()) {
            wrapper.eq(AuditLog::getRequestIp, request.getRequestIp());
        }
        if (request.getStartTime() != null) {
            wrapper.ge(AuditLog::getCreateTime, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            wrapper.le(AuditLog::getCreateTime, request.getEndTime());
        }
        if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
            wrapper.and(w -> w
                    .like(AuditLog::getOperation, request.getKeyword())
                    .or()
                    .like(AuditLog::getUsername, request.getKeyword())
                    .or()
                    .like(AuditLog::getErrorMsg, request.getKeyword())
            );
        }

        // 分页查询
        Page<AuditLog> page = new Page<>(request.getPage(), request.getSize());
        Page<AuditLog> resultPage = this.page(page, wrapper);

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("records", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("page", request.getPage());
        result.put("size", request.getSize());
        result.put("pages", resultPage.getPages());

        return result;
    }

    @Override
    public List<AuditLog> findRecentByUsername(String username, int limit) {
        return this.baseMapper.findRecentByUsername(username, limit);
    }

    @Override
    public List<AuditLog> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return this.baseMapper.findByTimeRange(startTime, endTime);
    }

    @Override
    public int countLoginFailures(String username, int minutes) {
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(minutes);
        return this.baseMapper.countLoginFailures(username, startTime);
    }

    @Override
    public Map<String, Object> getTodayLoginStats() {
        List<Map<String, Object>> stats = this.baseMapper.getTodayLoginStats();

        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failedCount = 0;

        for (Map<String, Object> stat : stats) {
            String resultStr = (String) stat.get("response_result");
            Long count = (Long) stat.get("count");

            if ("OK".equals(resultStr)) {
                successCount = count.intValue();
            } else if ("FAILED".equals(resultStr)) {
                failedCount = count.intValue();
            }
        }

        result.put("successCount", successCount);
        result.put("failedCount", failedCount);
        result.put("totalCount", successCount + failedCount);
        result.put("successRate", successCount + failedCount > 0 ?
                String.format("%.2f%%", (double) successCount / (successCount + failedCount) * 100) : "0%");

        return result;
    }

    @Override
    public List<Map<String, Object>> getOperationStats(LocalDateTime startTime, LocalDateTime endTime) {
        return this.baseMapper.getOperationStats(startTime, endTime);
    }
}
