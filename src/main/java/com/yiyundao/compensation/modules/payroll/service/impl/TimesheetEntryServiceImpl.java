package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.TimesheetEntryMapper;
import com.yiyundao.compensation.modules.payroll.entity.TimesheetEntry;
import com.yiyundao.compensation.modules.payroll.service.TimesheetEntryService;
import org.springframework.stereotype.Service;

@Service
public class TimesheetEntryServiceImpl extends ServiceImpl<TimesheetEntryMapper, TimesheetEntry> implements TimesheetEntryService {
}

