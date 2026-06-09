package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.interfaces.dto.openapi.OpenApiPayrollBatchDto;
import com.yiyundao.compensation.interfaces.dto.openapi.OpenApiPayrollLineDto;
import com.yiyundao.compensation.interfaces.dto.openapi.OpenApiPayslipDto;

import java.util.List;

public interface ExternalPayrollQueryService {

    Page<OpenApiPayrollBatchDto> pagePtBatches(String period,
                                               String status,
                                               long page,
                                               long size);

    OpenApiPayrollBatchDto findBatch(Long batchId);

    Page<OpenApiPayrollLineDto> pageBatchLines(Long batchId,
                                               String employeeRef,
                                               long page,
                                               long size);

    List<OpenApiPayslipDto> findPayslips(String employeeRef, String period);

    OpenApiPayslipDto findPayslip(Long payslipId, String employeeRef);
}
