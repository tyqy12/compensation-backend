package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollTaxBracket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayrollTaxBracketMapper extends BaseMapper<PayrollTaxBracket> {
}
