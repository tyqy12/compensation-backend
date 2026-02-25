package com.yiyundao.compensation.modules.audit.event;

import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 审计日志保存事件
 * <p>
 * 当审计日志保存后发布此事件，用于触发后续的闭环处理。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
@Getter
public class AuditLogSavedEvent extends ApplicationEvent {

    /**
     * 审计日志实体
     */
    private final AuditLog auditLog;

    /**
     * 构造函数
     *
     * @param source  事件源
     * @param auditLog 审计日志实体
     */
    public AuditLogSavedEvent(Object source, AuditLog auditLog) {
        super(source);
        this.auditLog = auditLog;
    }

    /**
     * 获取操作类型
     */
    public String getOperation() {
        return auditLog.getOperation();
    }

    /**
     * 获取用户名
     */
    public String getUsername() {
        return auditLog.getUsername();
    }

    /**
     * 获取业务类型
     */
    public String getBusinessType() {
        return auditLog.getBusinessType();
    }

    /**
     * 获取响应结果
     */
    public String getResponseResult() {
        return auditLog.getResponseResult();
    }

    /**
     * 是否是成功操作
     */
    public boolean isSuccess() {
        return "OK".equals(auditLog.getResponseResult());
    }

    /**
     * 是否有错误信息
     */
    public boolean hasError() {
        return auditLog.getErrorMsg() != null && !auditLog.getErrorMsg().isEmpty();
    }
}
