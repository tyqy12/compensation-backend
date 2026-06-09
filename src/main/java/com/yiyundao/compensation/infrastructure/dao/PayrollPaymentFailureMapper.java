package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollPaymentFailure;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayrollPaymentFailureMapper extends BaseMapper<PayrollPaymentFailure> {
}
