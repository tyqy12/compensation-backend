package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollDistribution;
import com.yiyundao.compensation.modules.payroll.entity.PayrollReconciliationTask;

public interface PayrollReconciliationTaskService extends IService<PayrollReconciliationTask> {

    PayrollReconciliationTask createOrRefresh(PayrollDistribution distribution);
}
