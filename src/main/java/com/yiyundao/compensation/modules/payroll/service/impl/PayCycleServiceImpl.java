package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.PayCycleMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import org.springframework.stereotype.Service;

@Service
public class PayCycleServiceImpl extends ServiceImpl<PayCycleMapper, PayCycle> implements PayCycleService {
}

