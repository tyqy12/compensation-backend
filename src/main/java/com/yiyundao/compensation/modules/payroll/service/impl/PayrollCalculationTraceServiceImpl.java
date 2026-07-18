package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.PayrollCalculationTraceMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollCalculationTrace;
import com.yiyundao.compensation.modules.payroll.service.PayrollCalculationTraceService;
import org.springframework.stereotype.Service;

@Service
public class PayrollCalculationTraceServiceImpl
        extends ServiceImpl<PayrollCalculationTraceMapper, PayrollCalculationTrace>
        implements PayrollCalculationTraceService {
}
