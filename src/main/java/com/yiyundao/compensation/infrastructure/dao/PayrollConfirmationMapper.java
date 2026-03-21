package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollConfirmation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayrollConfirmationMapper extends BaseMapper<PayrollConfirmation> {
}
