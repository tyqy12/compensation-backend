package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayrollDistributionMapper extends BaseMapper<PayrollDistribution> {
}
