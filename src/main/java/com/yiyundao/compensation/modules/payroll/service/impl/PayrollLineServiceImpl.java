package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.PayrollLineMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import org.springframework.stereotype.Service;

@Service
public class PayrollLineServiceImpl extends ServiceImpl<PayrollLineMapper, PayrollLine> implements PayrollLineService {
}

