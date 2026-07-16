package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.infrastructure.dao.SalaryTemplateVersionMapper;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplateVersion;
import com.yiyundao.compensation.modules.payroll.service.SalaryTemplateVersionService;
import org.springframework.stereotype.Service;

@Service
public class SalaryTemplateVersionServiceImpl
        extends ServiceImpl<SalaryTemplateVersionMapper, SalaryTemplateVersion>
        implements SalaryTemplateVersionService {
}
