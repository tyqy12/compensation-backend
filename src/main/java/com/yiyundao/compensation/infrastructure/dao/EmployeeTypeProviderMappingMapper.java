package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.payment.entity.EmployeeTypeProviderMapping;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工类型与服务商映射 Mapper
 */
@Mapper
public interface EmployeeTypeProviderMappingMapper extends BaseMapper<EmployeeTypeProviderMapping> {
}
