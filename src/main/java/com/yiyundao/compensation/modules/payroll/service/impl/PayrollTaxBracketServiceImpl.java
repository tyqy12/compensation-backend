package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.PayrollTaxBracketMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollTaxBracket;
import com.yiyundao.compensation.modules.payroll.service.PayrollTaxBracketService;
import org.springframework.stereotype.Service;

@Service
public class PayrollTaxBracketServiceImpl extends ServiceImpl<PayrollTaxBracketMapper, PayrollTaxBracket>
        implements PayrollTaxBracketService {
}
