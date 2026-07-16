package com.yiyundao.compensation.interfaces.controller.payroll;

import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.common.idempotent.Idempotent;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollDistributionDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollDistributionItemDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollReconciliationTaskDto;
import com.yiyundao.compensation.modules.payment.dto.PaymentBatchResponse;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payroll.service.PayrollFlowQueryService;
import com.yiyundao.compensation.modules.payroll.service.PayrollProcessManager;
import com.yiyundao.compensation.security.SecurityAnnotations;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/payroll/distributions")
@RequiredArgsConstructor
@SecurityAnnotations.IsFinanceOrAdmin
public class PayrollDistributionController {

    private final PayrollFlowQueryService payrollFlowQueryService;
    private final PayrollProcessManager payrollProcessManager;

    @GetMapping
    public ApiResponse<PageResponse<PayrollDistributionDto>> page(@RequestParam(defaultValue = "1") Integer page,
                                                                  @RequestParam(defaultValue = "10") Integer size,
                                                                  @RequestParam(required = false) Long batchId,
                                                                  @RequestParam(required = false) Integer batchRevision,
                                                                  @RequestParam(required = false) String status) {
        return ApiResponse.success(payrollFlowQueryService.pageDistributions(page, size, batchId, batchRevision, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<PayrollDistributionDto> detail(@PathVariable Long id) {
        PayrollDistributionDto dto = payrollFlowQueryService.getDistributionDetail(id);
        if (dto == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "发放单不存在");
        }
        return ApiResponse.success(dto);
    }

    @GetMapping("/{id}/items")
    public ApiResponse<List<PayrollDistributionItemDto>> items(@PathVariable Long id) {
        return ApiResponse.success(payrollFlowQueryService.listDistributionItems(id));
    }

    @GetMapping("/{id}/reconciliation")
    public ApiResponse<PayrollReconciliationTaskDto> reconciliation(@PathVariable Long id) {
        return ApiResponse.success(payrollFlowQueryService.getDistributionReconciliation(id));
    }

    @PostMapping("/{id}/retry")
    @Idempotent(key = "'payroll:distribution:retry:' + #p0", expireSeconds = 600,
            message = "发放单重试正在处理中，请勿重复提交", throwOnLockFail = true, deleteOnError = true)
    public ApiResponse<PaymentBatchResponse> retry(@PathVariable Long id) {
        PaymentBatch paymentBatch = payrollProcessManager.retryFailedDistribution(id);
        return ApiResponse.success(PaymentBatchResponse.from(paymentBatch));
    }
}
