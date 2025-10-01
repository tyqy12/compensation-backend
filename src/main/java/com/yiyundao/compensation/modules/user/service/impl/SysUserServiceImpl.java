package com.yiyundao.compensation.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.infrastructure.dao.SysUserMapper;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Override
    public SysUser findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).last("limit 1"));
    }

    @Override
    public SysUser findByPlatform(String platformType, String platformUserId) {
        return getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPlatformType, platformType)
                .eq(SysUser::getPlatformUserId, platformUserId)
                .last("limit 1"));
    }

    @Override
    public SysUser findFirstByRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        String normalized = roleCode.trim();
        return getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, "active")
                .like(SysUser::getRoles, normalized)
                .orderByAsc(SysUser::getId)
                .last("limit 1"));
    }
}
