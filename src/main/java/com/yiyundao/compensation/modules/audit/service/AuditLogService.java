package com.yiyundao.compensation.modules.audit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.audit.entity.AuditLog;

public interface AuditLogService extends IService<AuditLog> {
    void record(String operation,
                String method,
                String requestUrl,
                String requestIp,
                String userAgent,
                String businessType,
                String businessKey,
                String username,
                String requestParams,
                String responseResult,
                String errorMsg,
                Long executionTimeMs);
}
