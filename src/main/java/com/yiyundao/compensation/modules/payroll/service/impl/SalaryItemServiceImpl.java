package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.SalaryItemMapper;
import com.yiyundao.compensation.modules.payroll.entity.SalaryItem;
import com.yiyundao.compensation.modules.payroll.service.SalaryItemService;
import org.springframework.stereotype.Service;

@Service
public class SalaryItemServiceImpl extends ServiceImpl<SalaryItemMapper, SalaryItem> implements SalaryItemService {
}

