package com.yiyundao.compensation.modules.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.AppDataGrantMapper;
import com.yiyundao.compensation.modules.app.entity.AppDataGrant;
import com.yiyundao.compensation.modules.app.service.AppDataGrantService;
import com.yiyundao.compensation.modules.app.service.AppRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppDataGrantServiceImpl extends ServiceImpl<AppDataGrantMapper, AppDataGrant>
        implements AppDataGrantService {

    private static final Set<String> SUPPORTED_TYPES = Set.of(TENANT, DEPARTMENT, EMPLOYEE, PAYROLL_BATCH);

    private final AppRegistryService appRegistryService;

    @Override
    @Transactional
    public AppDataGrant saveValidated(AppDataGrant grant) {
        if (grant == null || grant.getAppId() == null
                || !StringUtils.hasText(grant.getScopeType())
                || !StringUtils.hasText(grant.getScopeValue())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "应用、数据范围类型和范围值不能为空");
        }
        if (appRegistryService.getById(grant.getAppId()) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "应用不存在");
        }
        String type = grant.getScopeType().trim().toLowerCase();
        String value = grant.getScopeValue().trim();
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "数据范围类型仅支持 tenant/department/employee/payroll_batch");
        }
        if ("*".equals(value)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "禁止使用通配数据范围，请明确指定租户、部门、员工或批次");
        }
        if (!TENANT.equals(type)) {
            try {
                Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR, "非租户数据范围必须使用数字 ID");
            }
        }
        grant.setScopeType(type);
        grant.setScopeValue(value);
        grant.setStatus("active");
        saveOrUpdate(grant);
        return grant;
    }

    @Override
    public List<AppDataGrant> listActiveByAppId(Long appId) {
        if (appId == null) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<AppDataGrant>()
                .eq(AppDataGrant::getAppId, appId)
                .eq(AppDataGrant::getStatus, "active")
                .orderByAsc(AppDataGrant::getScopeType)
                .orderByAsc(AppDataGrant::getScopeValue));
    }

    @Override
    @Transactional
    public void revoke(Long appId, Long grantId) {
        if (appId == null || grantId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "应用和授权 ID 不能为空");
        }
        AppDataGrant grant = getOne(new LambdaQueryWrapper<AppDataGrant>()
                .eq(AppDataGrant::getId, grantId)
                .eq(AppDataGrant::getAppId, appId)
                .eq(AppDataGrant::getStatus, "active")
                .last("limit 1"));
        if (grant == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "数据授权不存在");
        }
        grant.setStatus("revoked");
        updateById(grant);
    }
}
