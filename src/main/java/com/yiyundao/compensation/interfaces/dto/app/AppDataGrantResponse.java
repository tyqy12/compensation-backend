package com.yiyundao.compensation.interfaces.dto.app;

import com.yiyundao.compensation.modules.app.entity.AppDataGrant;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AppDataGrantResponse {
    private Long id;
    private Long appId;
    private String scopeType;
    private String scopeValue;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static AppDataGrantResponse from(AppDataGrant grant) {
        return AppDataGrantResponse.builder()
                .id(grant.getId())
                .appId(grant.getAppId())
                .scopeType(grant.getScopeType())
                .scopeValue(grant.getScopeValue())
                .status(grant.getStatus())
                .createTime(grant.getCreateTime())
                .updateTime(grant.getUpdateTime())
                .build();
    }
}
