package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollEnrollment;

public interface PayrollEnrollmentService extends IService<PayrollEnrollment> {
    PayrollEnrollment saveValidated(PayrollEnrollment enrollment);
}
