package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollEnrollment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayrollEnrollmentMapper extends BaseMapper<PayrollEnrollment> {
}
