package com.yiyundao.compensation.modules.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.infrastructure.dao.SysUserMapper;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
}
