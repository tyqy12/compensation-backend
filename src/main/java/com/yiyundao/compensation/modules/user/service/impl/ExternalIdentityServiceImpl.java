package com.yiyundao.compensation.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.ExternalIdentityMapper;
import com.yiyundao.compensation.interfaces.dto.config.DingTalkConfigDto;
import com.yiyundao.compensation.interfaces.dto.config.FeishuConfigDto;
import com.yiyundao.compensation.interfaces.dto.config.WechatConfigDto;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalIdentityServiceImpl extends ServiceImpl<ExternalIdentityMapper, ExternalIdentity>
        implements ExternalIdentityService {

    private final IntegrationConfigService integrationConfigService;

    @Override
    public ExternalIdentity findActiveIdentity(String provider, String tenantKey, String subjectType, String subjectId) {
        String normalizedProvider = normalizeProvider(provider);
        String normalizedTenant = normalizeTenantKey(normalizedProvider, tenantKey);
        String normalizedSubjectType = normalizeSubjectType(subjectType);
        if (!StringUtils.hasText(normalizedProvider) || !StringUtils.hasText(subjectId)) {
            return null;
        }
        ExternalIdentity identity = getOne(baseIdentitySelect()
                .eq(ExternalIdentity::getProvider, normalizedProvider)
                .eq(ExternalIdentity::getTenantKey, normalizedTenant)
                .eq(ExternalIdentity::getSubjectType, normalizedSubjectType)
                .eq(ExternalIdentity::getSubjectId, subjectId)
                .eq(ExternalIdentity::getStatus, STATUS_ACTIVE)
                .last("limit 1"));
        if (identity == null && !DEFAULT_SUBJECT_TYPE.equals(normalizedSubjectType)) {
            identity = getOne(baseIdentitySelect()
                    .eq(ExternalIdentity::getProvider, normalizedProvider)
                    .eq(ExternalIdentity::getTenantKey, normalizedTenant)
                    .eq(ExternalIdentity::getSubjectType, DEFAULT_SUBJECT_TYPE)
                    .eq(ExternalIdentity::getSubjectId, subjectId)
                    .eq(ExternalIdentity::getStatus, STATUS_ACTIVE)
                    .last("limit 1"));
        }
        if (identity == null && !DEFAULT_TENANT_KEY.equals(normalizedTenant)) {
            identity = getOne(baseIdentitySelect()
                    .eq(ExternalIdentity::getProvider, normalizedProvider)
                    .eq(ExternalIdentity::getTenantKey, DEFAULT_TENANT_KEY)
                    .eq(ExternalIdentity::getSubjectType, normalizedSubjectType)
                    .eq(ExternalIdentity::getSubjectId, subjectId)
                    .eq(ExternalIdentity::getStatus, STATUS_ACTIVE)
                    .last("limit 1"));
            if (identity == null && !DEFAULT_SUBJECT_TYPE.equals(normalizedSubjectType)) {
                identity = getOne(baseIdentitySelect()
                        .eq(ExternalIdentity::getProvider, normalizedProvider)
                        .eq(ExternalIdentity::getTenantKey, DEFAULT_TENANT_KEY)
                        .eq(ExternalIdentity::getSubjectType, DEFAULT_SUBJECT_TYPE)
                        .eq(ExternalIdentity::getSubjectId, subjectId)
                        .eq(ExternalIdentity::getStatus, STATUS_ACTIVE)
                        .last("limit 1"));
            }
        }
        return identity;
    }

    @Override
    public Long findBoundUserId(String provider, String tenantKey, String subjectType, String subjectId) {
        ExternalIdentity identity = findActiveIdentity(provider, tenantKey, subjectType, subjectId);
        return identity != null ? identity.getUserId() : null;
    }

    @Override
    public Long findBoundEmployeeId(String provider, String tenantKey, String subjectType, String subjectId) {
        ExternalIdentity identity = findActiveIdentity(provider, tenantKey, subjectType, subjectId);
        return identity != null ? identity.getEmployeeId() : null;
    }

    @Override
    public ExternalIdentity findPrimaryByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return getOne(baseIdentitySelect()
                .eq(ExternalIdentity::getUserId, userId)
                .eq(ExternalIdentity::getStatus, STATUS_ACTIVE)
                .orderByDesc(ExternalIdentity::getPrimaryFlag)
                .orderByDesc(ExternalIdentity::getLastSeenAt)
                .orderByDesc(ExternalIdentity::getId)
                .last("limit 1"));
    }

    @Override
    public ExternalIdentity findPrimaryByEmployeeId(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        return getOne(baseIdentitySelect()
                .eq(ExternalIdentity::getEmployeeId, employeeId)
                .eq(ExternalIdentity::getStatus, STATUS_ACTIVE)
                .orderByDesc(ExternalIdentity::getPrimaryFlag)
                .orderByDesc(ExternalIdentity::getLastSeenAt)
                .orderByDesc(ExternalIdentity::getId)
                .last("limit 1"));
    }

    @Override
    public ExternalIdentity findByUserIdAndProvider(Long userId, String provider) {
        if (userId == null || !StringUtils.hasText(provider)) {
            return null;
        }
        String normalizedProvider = normalizeProvider(provider);
        return getOne(baseIdentitySelect()
                .eq(ExternalIdentity::getUserId, userId)
                .eq(ExternalIdentity::getProvider, normalizedProvider)
                .eq(ExternalIdentity::getStatus, STATUS_ACTIVE)
                .orderByDesc(ExternalIdentity::getPrimaryFlag)
                .orderByDesc(ExternalIdentity::getLastSeenAt)
                .orderByDesc(ExternalIdentity::getId)
                .last("limit 1"));
    }

    @Override
    @Transactional
    public void upsertPlatformIdentity(String provider, String tenantKey, String subjectType, String subjectId,
                                       Long employeeId, Long userId, String source, boolean primary) {
        String normalizedProvider = normalizeProvider(provider);
        String normalizedTenant = normalizeTenantKey(normalizedProvider, tenantKey);
        String normalizedSubjectType = normalizeSubjectType(subjectType);
        if (!StringUtils.hasText(normalizedProvider) || !StringUtils.hasText(subjectId)) {
            return;
        }

        ExternalIdentity existing = findActiveIdentity(normalizedProvider, normalizedTenant, normalizedSubjectType, subjectId);
        if (existing == null) {
            ExternalIdentity identity = new ExternalIdentity();
            identity.setProvider(normalizedProvider);
            identity.setTenantKey(normalizedTenant);
            identity.setSubjectType(normalizedSubjectType);
            identity.setSubjectId(subjectId);
            identity.setEmployeeId(employeeId);
            identity.setUserId(userId);
            identity.setPrimaryFlag(primary);
            identity.setStatus(STATUS_ACTIVE);
            identity.setSource(source);
            identity.setBoundAt(LocalDateTime.now());
            identity.setLastSeenAt(LocalDateTime.now());
            save(identity);
            return;
        }

        if (userId != null && existing.getUserId() != null && !userId.equals(existing.getUserId())) {
            throw new IllegalStateException("外部身份已绑定其他用户，provider=" + normalizedProvider + ", subjectId=" + subjectId);
        }
        if (employeeId != null && existing.getEmployeeId() != null && !employeeId.equals(existing.getEmployeeId())) {
            throw new IllegalStateException("外部身份已绑定其他员工，provider=" + normalizedProvider + ", subjectId=" + subjectId);
        }

        boolean changed = false;
        if (employeeId != null && existing.getEmployeeId() == null) {
            existing.setEmployeeId(employeeId);
            changed = true;
        }
        if (userId != null && existing.getUserId() == null) {
            existing.setUserId(userId);
            changed = true;
        }
        if (!STATUS_ACTIVE.equals(existing.getStatus())) {
            existing.setStatus(STATUS_ACTIVE);
            existing.setUnboundAt(null);
            changed = true;
        }
        if (primary && !Boolean.TRUE.equals(existing.getPrimaryFlag())) {
            existing.setPrimaryFlag(true);
            changed = true;
        }
        if (StringUtils.hasText(source) && !source.equals(existing.getSource())) {
            existing.setSource(source);
            changed = true;
        }
        existing.setLastSeenAt(LocalDateTime.now());
        if (existing.getBoundAt() == null) {
            existing.setBoundAt(LocalDateTime.now());
            changed = true;
        }
        if (!changed) {
            log.debug("刷新外部身份最近访问时间: provider={}, subjectType={}, subjectId={}",
                    normalizedProvider, normalizedSubjectType, subjectId);
        }
        updateById(existing);
    }

    @Override
    @Transactional
    public void deactivatePlatformIdentity(String provider, String tenantKey, String subjectType, String subjectId,
                                           Long employeeId, Long userId, String source) {
        ExternalIdentity existing = findActiveIdentity(provider, tenantKey, subjectType, subjectId);
        if (existing == null) {
            return;
        }
        if (employeeId != null && existing.getEmployeeId() != null && !employeeId.equals(existing.getEmployeeId())) {
            log.warn("跳过解绑：员工不匹配，provider={}, subjectId={}, expectEmp={}, actualEmp={}",
                    provider, subjectId, employeeId, existing.getEmployeeId());
            return;
        }
        if (userId != null && existing.getUserId() != null && !userId.equals(existing.getUserId())) {
            log.warn("跳过解绑：用户不匹配，provider={}, subjectId={}, expectUser={}, actualUser={}",
                    provider, subjectId, userId, existing.getUserId());
            return;
        }
        existing.setStatus(STATUS_INACTIVE);
        existing.setUnboundAt(LocalDateTime.now());
        if (StringUtils.hasText(source)) {
            existing.setSource(source);
        }
        updateById(existing);
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        String p = provider.trim().toLowerCase();
        return switch (p) {
            case "wechat", "wecom", "qywx", "wx" -> "wechat";
            case "dingtalk", "dingding", "dd" -> "dingtalk";
            case "feishu", "lark" -> "feishu";
            default -> p;
        };
    }

    private String normalizeTenantKey(String provider, String tenantKey) {
        if (StringUtils.hasText(tenantKey) && !DEFAULT_TENANT_KEY.equalsIgnoreCase(tenantKey.trim())) {
            return tenantKey.trim();
        }
        String resolved = resolveTenantFromConfig(provider);
        return StringUtils.hasText(resolved) ? resolved : DEFAULT_TENANT_KEY;
    }

    private String normalizeSubjectType(String subjectType) {
        if (!StringUtils.hasText(subjectType)) {
            return DEFAULT_SUBJECT_TYPE;
        }
        return subjectType.trim().toLowerCase();
    }

    private String resolveTenantFromConfig(String provider) {
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        return switch (provider) {
            case "wechat" -> {
                WechatConfigDto cfg = integrationConfigService.getWechatConfig();
                yield cfg != null ? trimToNull(cfg.getCorpId()) : null;
            }
            case "dingtalk" -> {
                DingTalkConfigDto cfg = integrationConfigService.getDingTalkConfig();
                yield cfg != null ? trimToNull(cfg.getAppKey()) : null;
            }
            case "feishu" -> {
                FeishuConfigDto cfg = integrationConfigService.getFeishuConfig();
                yield cfg != null ? trimToNull(cfg.getAppId()) : null;
            }
            default -> null;
        };
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 显式列选择，避免 ORM 在某些运行时环境下生成保留关键字别名导致 SQL 语法错误。
     */
    private LambdaQueryWrapper<ExternalIdentity> baseIdentitySelect() {
        return new LambdaQueryWrapper<ExternalIdentity>()
                .select(
                        ExternalIdentity::getId,
                        ExternalIdentity::getProvider,
                        ExternalIdentity::getTenantKey,
                        ExternalIdentity::getSubjectType,
                        ExternalIdentity::getSubjectId,
                        ExternalIdentity::getEmployeeId,
                        ExternalIdentity::getUserId,
                        ExternalIdentity::getStatus,
                        ExternalIdentity::getSource,
                        ExternalIdentity::getBoundAt,
                        ExternalIdentity::getUnboundAt,
                        ExternalIdentity::getLastSeenAt,
                        ExternalIdentity::getExtJson,
                        ExternalIdentity::getCreateTime,
                        ExternalIdentity::getUpdateTime,
                        ExternalIdentity::getCreateBy,
                        ExternalIdentity::getUpdateBy,
                        ExternalIdentity::getDeleted,
                        ExternalIdentity::getVersion
                );
    }
}
