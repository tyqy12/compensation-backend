package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.SalaryTemplateMapper;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplate;
import com.yiyundao.compensation.modules.payroll.service.SalaryTemplateService;
import org.springframework.stereotype.Service;

@Service
public class SalaryTemplateServiceImpl extends ServiceImpl<SalaryTemplateMapper, SalaryTemplate> implements SalaryTemplateService {
}

