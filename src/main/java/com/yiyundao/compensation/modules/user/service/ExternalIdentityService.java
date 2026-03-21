package com.yiyundao.compensation.modules.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;

public interface ExternalIdentityService extends IService<ExternalIdentity> {

    String DEFAULT_TENANT_KEY = "default";
    String DEFAULT_SUBJECT_TYPE = "platform_user_id";
    String STATUS_ACTIVE = "active";
    String STATUS_INACTIVE = "inactive";

    ExternalIdentity findActiveIdentity(String provider, String tenantKey, String subjectType, String subjectId);

    Long findBoundUserId(String provider, String tenantKey, String subjectType, String subjectId);

    Long findBoundEmployeeId(String provider, String tenantKey, String subjectType, String subjectId);

    ExternalIdentity findPrimaryByUserId(Long userId);

    ExternalIdentity findPrimaryByEmployeeId(Long employeeId);

    ExternalIdentity findByUserIdAndProvider(Long userId, String provider);

    void upsertPlatformIdentity(String provider, String tenantKey, String subjectType, String subjectId,
                                Long employeeId, Long userId, String source, boolean primary);

    void deactivatePlatformIdentity(String provider, String tenantKey, String subjectType, String subjectId,
                                    Long employeeId, Long userId, String source);
}
