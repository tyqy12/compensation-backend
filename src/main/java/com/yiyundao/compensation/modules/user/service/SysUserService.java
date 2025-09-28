package com.yiyundao.compensation.modules.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.user.entity.SysUser;

public interface SysUserService extends IService<SysUser> {
    SysUser findByUsername(String username);
    SysUser findByPlatform(String platformType, String platformUserId);
}
