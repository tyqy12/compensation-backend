package com.yiyundao.compensation.interfaces.dto.admin;

import com.yiyundao.compensation.common.utils.SecretLogSanitizer;
import com.yiyundao.compensation.modules.audit.entity.AuditLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogResponseDto {

    private Long id;
    private String username;
    private String operation;
    private String method;
    private String requestUrl;
    private String requestIp;
    private String userAgent;
    private String requestParams;
    private String responseResult;
    private String errorMsg;
    private Long executionTime;
    private String businessType;
    private String businessKey;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static AuditLogResponseDto from(AuditLog log) {
        if (log == null) {
            return null;
        }
        return AuditLogResponseDto.builder()
                .id(log.getId())
                .username(log.getUsername())
                .operation(log.getOperation())
                .method(log.getMethod())
                .requestUrl(log.getRequestUrl())
                .requestIp(log.getRequestIp())
                .userAgent(log.getUserAgent())
                .requestParams(SecretLogSanitizer.sanitize(log.getRequestParams()))
                .responseResult(SecretLogSanitizer.sanitize(log.getResponseResult()))
                .errorMsg(SecretLogSanitizer.sanitize(log.getErrorMsg()))
                .executionTime(log.getExecutionTime())
                .businessType(log.getBusinessType())
                .businessKey(log.getBusinessKey())
                .createTime(log.getCreateTime())
                .updateTime(log.getUpdateTime())
                .build();
    }
}
