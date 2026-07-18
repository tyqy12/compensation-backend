package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.PayrollContributionRecordMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollContributionRecord;
import com.yiyundao.compensation.modules.payroll.service.PayrollContributionRecordService;
import org.springframework.stereotype.Service;

@Service
public class PayrollContributionRecordServiceImpl
        extends ServiceImpl<PayrollContributionRecordMapper, PayrollContributionRecord>
        implements PayrollContributionRecordService {
}
