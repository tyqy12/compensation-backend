package com.yiyundao.compensation.modules.payroll.service;

import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBasicReportDto;

public interface PayrollReportService {

    PayrollBasicReportDto basicReport(Long batchId, String periodLabel, String department);

    byte[] exportBasicReport(Long batchId, String periodLabel, String department);
}

