package com.yiyundao.compensation.interfaces.controller.payroll;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollType;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchCreateRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchUpdateRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollLedgerDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollManagerReviewDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.interfaces.vo.payroll.PayrollBatchSummaryVO;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollCalculationService;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.security.SecurityAnnotations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/payroll/batches")
@RequiredArgsConstructor
@Tag(name = "薪资批次管理", description = "薪资批次的创建、审批、计算和支付")
@SecurityRequirement(name = "Bearer")
public class PayrollBatchController {

    private final PayrollBatchService payrollBatchService;
    private final PayrollCalculationService calculationService;
    private final PayrollBatchMapper payrollBatchMapper;

    @PostMapping
    @SecurityAnnotations.IsFinanceOrHrOrAdmin
    @Operation(summary = "创建薪资批次", description = "创建新的薪资批次，默认为草稿状态")
    public ApiResponse<PayrollBatch> create(@Valid @RequestBody PayrollBatchCreateRequest req) {
        PayrollBatch b = mapToEntity(req);
        payrollBatchService.save(b);
        return ApiResponse.success(b);
    }

    @PutMapping("/{id}")
    @SecurityAnnotations.IsFinanceOrHrOrAdmin
    @Operation(summary = "更新薪资批次", description = "更新批次信息，仅草稿状态可修改")
    public ApiResponse<PayrollBatch> update(@PathVariable Long id, @Valid @RequestBody PayrollBatchUpdateRequest req) {
        PayrollBatch b = payrollBatchService.getById(id);
        if (b == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        PayrollBatchStatus status = b.getStatus();
        if (status == null || !status.canEdit()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前状态不可修改");
        }
        updateEntity(b, req);
        payrollBatchService.updateById(b);
        return ApiResponse.success(b);
    }

    @PostMapping("/{id}/lock")
    @SecurityAnnotations.IsFinanceOrAdmin
    @Operation(summary = "锁定薪资批次", description = "锁定批次后开始计算薪资")
    public ApiResponse<Boolean> lock(@PathVariable Long id) {
        PayrollBatch batch = payrollBatchService.getById(id);
        if (batch == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        boolean ok = payrollBatchService.lockBatch(id);
        if (!ok) throw new BusinessException(ErrorCode.INVALID_STATUS, "批次状态不允许锁定");
        return ApiResponse.success(true);
    }

    @PostMapping("/{id}/submit-approval")
    @SecurityAnnotations.IsFinanceOrAdmin
    @Operation(summary = "提交审批", description = "提交薪资批次进入审批流程")
    public ApiResponse<Boolean> submitApproval(@PathVariable Long id) {
        PayrollBatch batch = payrollBatchService.getById(id);
        if (batch == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        boolean ok = payrollBatchService.submitForApproval(id);
        if (!ok) throw new BusinessException(ErrorCode.INVALID_STATUS, "提交审批失败，请检查批次状态");
        return ApiResponse.success(true);
    }

    @PostMapping("/{id}/dry-run")
    @SecurityAnnotations.IsFinanceOrManagerOrAdmin
    @Operation(summary = "薪资试算", description = "对批次进行薪资试算预览")
    public ApiResponse<PayrollPreviewDto> dryRun(@PathVariable Long id) {
        PayrollBatch batch = payrollBatchService.getById(id);
        if (batch == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        PayrollPreviewDto preview = calculationService.dryRunPreview(id);
        if (preview == null) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "批次不可试算或数据缺失");
        return ApiResponse.success(preview);
    }

    @PostMapping("/{id}/compute")
    @SecurityAnnotations.IsFinanceOrAdmin
    @Operation(summary = "计算薪资", description = "执行薪资计算并落地结果")
    public ApiResponse<Boolean> compute(@PathVariable Long id) {
        PayrollBatch batch = payrollBatchService.getById(id);
        if (batch == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        PayrollBatchStatus status = batch.getStatus();
        if (status == null || !status.canCompute()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前状态不可计算，请锁定或审批通过后再试");
        }
        boolean ok = calculationService.computeAndSave(id);
        if (!ok) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "计算落地失败，请确认批次状态及数据");
        return ApiResponse.success(true);
    }

    @GetMapping("/{id}/ledger")
    @SecurityAnnotations.IsFinanceOrAdmin
    @Operation(summary = "获取薪资台账", description = "获取批次薪资明细台账")
    public ApiResponse<PayrollLedgerDto> ledger(@PathVariable Long id) {
        return ApiResponse.success(calculationService.ledger(id));
    }

    @GetMapping("/{id}/manager-review")
    @SecurityAnnotations.IsFinanceOrManagerOrAdmin
    @Operation(summary = "主管审核视图", description = "获取主管审核所需数据")
    public ApiResponse<PayrollManagerReviewDto> managerReview(@PathVariable Long id,
                                                              @RequestParam(required = false) String department,
                                                              @RequestParam(required = false) Long managerId,
                                                              @RequestParam(required = false) String keyword) {
        return ApiResponse.success(calculationService.managerReview(id, department, managerId, keyword));
    }

    @GetMapping
    @SecurityAnnotations.IsFinanceOrHrOrManagerOrAdmin
    @Operation(summary = "分页查询批次", description = "支持多条件筛选和分页，返回汇总数据")
    public ApiResponse<PageResponse<PayrollBatchSummaryVO>> list(@RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "10") int size,
                                                                 @RequestParam(required = false) String type,
                                                                 @RequestParam(required = false) String periodLabel,
                                                                 @RequestParam(required = false) String status) {
        int offset = (page - 1) * size;
        List<PayrollBatchSummaryVO> records = payrollBatchMapper.selectBatchSummaryList(type, periodLabel, status, offset, size);
        long total = payrollBatchMapper.countBatchSummary(type, periodLabel, status);
        return ApiResponse.success(PageResponse.of(records, page, size, total));
    }

    @GetMapping("/{id}")
    @SecurityAnnotations.IsFinanceOrHrOrManagerOrAdmin
    @Operation(summary = "获取批次详情", description = "根据ID获取批次详细信息")
    public ApiResponse<PayrollBatch> get(@PathVariable Long id) {
        return ApiResponse.success(payrollBatchService.getById(id));
    }

    private PayrollBatch mapToEntity(PayrollBatchCreateRequest req) {
        PayrollBatch b = new PayrollBatch();
        b.setPayCycleId(req.getPayCycleId());
        b.setPeriodLabel(req.getPeriodLabel());
        b.setType(req.getType() != null ? req.getType() : PayrollType.FULL_TIME.getCode());
        b.setScopeJson(req.getScopeJson());
        b.setCurrency(req.getCurrency() != null ? req.getCurrency() : "CNY");
        b.setStatus(PayrollBatchStatus.DRAFT);
        b.setRemark(req.getRemark());
        return b;
    }

    private void updateEntity(PayrollBatch b, PayrollBatchUpdateRequest req) {
        if (req.getPayCycleId() != null) b.setPayCycleId(req.getPayCycleId());
        if (req.getPeriodLabel() != null) b.setPeriodLabel(req.getPeriodLabel());
        if (req.getScopeJson() != null) b.setScopeJson(req.getScopeJson());
        if (req.getCurrency() != null) b.setCurrency(req.getCurrency());
        if (req.getRemark() != null) b.setRemark(req.getRemark());
    }
}
