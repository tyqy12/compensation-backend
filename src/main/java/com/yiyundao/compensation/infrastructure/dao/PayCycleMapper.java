package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayCycleMapper extends BaseMapper<PayCycle> {}

