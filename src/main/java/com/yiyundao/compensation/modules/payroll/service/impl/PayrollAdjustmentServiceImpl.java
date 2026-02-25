package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.PayrollAdjustmentMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollAdjustment;
import com.yiyundao.compensation.modules.payroll.service.PayrollAdjustmentService;
import org.springframework.stereotype.Service;

@Service
public class PayrollAdjustmentServiceImpl extends ServiceImpl<PayrollAdjustmentMapper, PayrollAdjustment> implements PayrollAdjustmentService {
}

