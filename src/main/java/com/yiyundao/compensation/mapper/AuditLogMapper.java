package com.yiyundao.compensation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}