package com.yiyundao.compensation.interfaces.controller.payroll;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchCreateRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchUpdateRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollLedgerDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollManagerReviewDto;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollCalculationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payroll/batches")
@RequiredArgsConstructor
public class PayrollBatchController {

    private final PayrollBatchService payrollBatchService;
    private final PayrollCalculationService calculationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','HR')")
    public ApiResponse<PayrollBatch> create(@Valid @RequestBody PayrollBatchCreateRequest req) {
        PayrollBatch b = new PayrollBatch();
        b.setPayCycleId(req.getPayCycleId());
        b.setPeriodLabel(req.getPeriodLabel());
        b.setType(req.getType());
        b.setScopeJson(req.getScopeJson());
        b.setCurrency(req.getCurrency() == null ? "CNY" : req.getCurrency());
        b.setStatus("draft");
        b.setRemark(req.getRemark());
        payrollBatchService.save(b);
        return ApiResponse.success(b);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','HR')")
    public ApiResponse<PayrollBatch> update(@PathVariable Long id, @Valid @RequestBody PayrollBatchUpdateRequest req) {
        PayrollBatch b = payrollBatchService.getById(id);
        if (b == null) return ApiResponse.error(404, "batch not found");
        if (!"draft".equalsIgnoreCase(b.getStatus())) {
            return ApiResponse.error(400, "当前状态不可修改");
        }
        if (req.getPayCycleId() != null) b.setPayCycleId(req.getPayCycleId());
        if (req.getPeriodLabel() != null) b.setPeriodLabel(req.getPeriodLabel());
        if (req.getScopeJson() != null) b.setScopeJson(req.getScopeJson());
        if (req.getCurrency() != null) b.setCurrency(req.getCurrency());
        if (req.getRemark() != null) b.setRemark(req.getRemark());
        payrollBatchService.updateById(b);
        return ApiResponse.success(b);
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ApiResponse<Boolean> lock(@PathVariable Long id) {
        PayrollBatch batch = payrollBatchService.getById(id);
        if (batch == null) {
            return ApiResponse.error(404, "batch not found");
        }
        boolean ok = payrollBatchService.lockBatch(id);
        if (!ok) {
            return ApiResponse.error(400, "批次状态不允许锁定");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/{id}/submit-approval")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ApiResponse<Boolean> submitApproval(@PathVariable Long id) {
        PayrollBatch batch = payrollBatchService.getById(id);
        if (batch == null) {
            return ApiResponse.error(404, "batch not found");
        }
        boolean ok = payrollBatchService.submitForApproval(id);
        if (!ok) {
            return ApiResponse.error(400, "提交审批失败，请检查批次状态");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/{id}/dry-run")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','MANAGER')")
    public ApiResponse<com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto> dryRun(@PathVariable Long id) {
        PayrollBatch batch = payrollBatchService.getById(id);
        if (batch == null) {
            return ApiResponse.error(404, "batch not found");
        }
        var preview = calculationService.dryRunPreview(id);
        if (preview == null) {
            return ApiResponse.error(400, "批次不可试算或数据缺失");
        }
        return ApiResponse.success(preview);
    }

    @PostMapping("/{id}/compute")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ApiResponse<Boolean> compute(@PathVariable Long id) {
        PayrollBatch batch = payrollBatchService.getById(id);
        if (batch == null) {
            return ApiResponse.error(404, "batch not found");
        }
        String status = batch.getStatus() == null ? "" : batch.getStatus().toLowerCase();
        if (!("locked".equals(status) || "approved".equals(status))) {
            return ApiResponse.error(400, "当前状态不可计算，请锁定或审批通过后再试");
        }
        boolean ok = calculationService.computeAndSave(id);
        if (!ok) {
            return ApiResponse.error(400, "计算落地失败，请确认批次状态及数据");
        }
        return ApiResponse.success(true);
    }

    @GetMapping("/{id}/ledger")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ApiResponse<PayrollLedgerDto> ledger(@PathVariable Long id) {
        return ApiResponse.success(calculationService.ledger(id));
    }

    @GetMapping("/{id}/manager-review")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','MANAGER')")
    public ApiResponse<PayrollManagerReviewDto> managerReview(@PathVariable Long id,
                                                             @RequestParam(required = false) String department,
                                                             @RequestParam(required = false) Long managerId,
                                                             @RequestParam(required = false) String keyword) {
        return ApiResponse.success(calculationService.managerReview(id, department, managerId, keyword));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','MANAGER','HR')")
    public ApiResponse<Page<PayrollBatch>> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(required = false) String periodLabel,
                                               @RequestParam(required = false) String status) {
        Page<PayrollBatch> p = new Page<>(page, size);
        LambdaQueryWrapper<PayrollBatch> qw = new LambdaQueryWrapper<>();
        if (type != null && !type.isBlank()) qw.eq(PayrollBatch::getType, type);
        if (periodLabel != null && !periodLabel.isBlank()) qw.eq(PayrollBatch::getPeriodLabel, periodLabel);
        if (status != null && !status.isBlank()) qw.eq(PayrollBatch::getStatus, status);
        return ApiResponse.success(payrollBatchService.page(p, qw));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','MANAGER','HR')")
    public ApiResponse<PayrollBatch> get(@PathVariable Long id) {
        return ApiResponse.success(payrollBatchService.getById(id));
    }
}
