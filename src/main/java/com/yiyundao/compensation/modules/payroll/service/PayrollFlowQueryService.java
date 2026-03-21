package com.yiyundao.compensation.modules.payroll.service;

import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollDistributionDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollDistributionItemDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollReconciliationTaskDto;

import java.util.List;

public interface PayrollFlowQueryService {

    PageResponse<PayrollDistributionDto> pageDistributions(Integer page,
                                                           Integer size,
                                                           Long batchId,
                                                           Integer batchRevision,
                                                           String distributionStatus);

    PayrollDistributionDto getDistributionDetail(Long distributionId);

    List<PayrollDistributionItemDto> listDistributionItems(Long distributionId);

    PayrollReconciliationTaskDto getDistributionReconciliation(Long distributionId);

    PageResponse<PayrollReconciliationTaskDto> pageReconciliations(Integer page,
                                                                   Integer size,
                                                                   Long batchId,
                                                                   Integer batchRevision,
                                                                   Long distributionId,
                                                                   String taskStatus,
                                                                   String result);

    PayrollReconciliationTaskDto getReconciliationDetail(Long reconciliationTaskId);
}
