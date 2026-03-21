package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExternalIdentityMapper extends BaseMapper<ExternalIdentity> {
}
