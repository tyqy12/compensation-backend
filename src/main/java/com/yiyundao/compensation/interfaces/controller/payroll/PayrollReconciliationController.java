package com.yiyundao.compensation.interfaces.controller.payroll;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollReconciliationTaskDto;
import com.yiyundao.compensation.modules.payroll.service.PayrollFlowQueryService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payroll/reconciliations")
@RequiredArgsConstructor
@SecurityAnnotations.IsFinanceOrAdmin
public class PayrollReconciliationController {

    private final PayrollFlowQueryService payrollFlowQueryService;

    @GetMapping
    public ApiResponse<PageResponse<PayrollReconciliationTaskDto>> page(@RequestParam(defaultValue = "1") Integer page,
                                                                        @RequestParam(defaultValue = "10") Integer size,
                                                                        @RequestParam(required = false) Long batchId,
                                                                        @RequestParam(required = false) Integer batchRevision,
                                                                        @RequestParam(required = false) Long distributionId,
                                                                        @RequestParam(required = false) String taskStatus,
                                                                        @RequestParam(required = false) String result) {
        return ApiResponse.success(
                payrollFlowQueryService.pageReconciliations(page, size, batchId, batchRevision, distributionId, taskStatus, result)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<PayrollReconciliationTaskDto> detail(@PathVariable Long id) {
        PayrollReconciliationTaskDto dto = payrollFlowQueryService.getReconciliationDetail(id);
        if (dto == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "对账任务不存在");
        }
        return ApiResponse.success(dto);
    }
}
