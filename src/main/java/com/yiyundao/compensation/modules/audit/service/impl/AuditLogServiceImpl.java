package com.yiyundao.compensation.modules.audit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import com.yiyundao.compensation.infrastructure.dao.AuditLogMapper;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import org.springframework.stereotype.Service;

@Service
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements AuditLogService {

    @Override
    public void record(String operation, String method, String requestUrl, String requestIp, String userAgent,
                       String businessType, String businessKey, String username, String requestParams,
                       String responseResult, String errorMsg, Long executionTimeMs) {
        AuditLog log = new AuditLog();
        log.setOperation(operation);
        log.setMethod(method);
        log.setRequestUrl(requestUrl);
        log.setRequestIp(requestIp);
        log.setUserAgent(userAgent);
        log.setBusinessType(businessType);
        log.setBusinessKey(businessKey);
        log.setUsername(username);
        log.setRequestParams(requestParams);
        log.setResponseResult(responseResult);
        log.setErrorMsg(errorMsg);
        log.setExecutionTime(executionTimeMs);
        save(log);
    }
}
