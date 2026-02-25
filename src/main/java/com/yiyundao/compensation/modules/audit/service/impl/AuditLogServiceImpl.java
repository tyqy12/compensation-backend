package com.yiyundao.compensation.modules.audit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import com.yiyundao.compensation.modules.audit.event.AuditLogSavedEvent;
import com.yiyundao.compensation.infrastructure.dao.AuditLogMapper;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements AuditLogService {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void record(String operation, String method, String requestUrl, String requestIp, String userAgent,
                       String businessType, String businessKey, String username, String requestParams,
                       String responseResult, String errorMsg, Long executionTimeMs) {
        AuditLog auditLog = new AuditLog();
        auditLog.setOperation(operation);
        auditLog.setMethod(method);
        auditLog.setRequestUrl(requestUrl);
        auditLog.setRequestIp(requestIp);
        auditLog.setUserAgent(userAgent);
        auditLog.setBusinessType(businessType);
        auditLog.setBusinessKey(businessKey);
        auditLog.setUsername(username);
        auditLog.setRequestParams(requestParams);
        auditLog.setResponseResult(responseResult);
        auditLog.setErrorMsg(errorMsg);
        auditLog.setExecutionTime(executionTimeMs);

        // 保存审计日志
        save(auditLog);

        // 发布审计日志保存事件，触发后续闭环处理
        try {
            AuditLogSavedEvent event = new AuditLogSavedEvent(this, auditLog);
            eventPublisher.publishEvent(event);
            log.debug("审计日志事件已发布: operation={}, username={}", operation, username);
        } catch (Exception e) {
            log.error("发布审计日志事件失败: operation={}", operation, e);
        }
    }
}
