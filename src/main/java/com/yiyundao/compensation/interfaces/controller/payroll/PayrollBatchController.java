package com.yiyundao.compensation.interfaces.controller.payroll;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.common.idempotent.Idempotent;
import com.yiyundao.compensation.enums.PayrollConfirmationMode;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollCalculationStatus;
import com.yiyundao.compensation.enums.PayrollType;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchCreateRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchResponseDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchUpdateRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollLedgerDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollManagerReviewDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.interfaces.vo.payroll.PayrollBatchSummaryVO;
import com.yiyundao.compensation.modules.payment.dto.PaymentBatchResponse;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollCalculationService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import com.yiyundao.compensation.modules.payroll.service.PayrollProcessManager;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.security.SecurityAnnotations;
import com.yiyundao.compensation.security.SecurityConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/payroll/batches")
@RequiredArgsConstructor
@Tag(name = "薪资批次管理", description = "薪资批次的创建、审批、计算和支付")
@SecurityRequirement(name = "Bearer")
public class PayrollBatchController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    private final PayrollBatchService payrollBatchService;
    private final PayrollCalculationService calculationService;
    private final PayrollBatchMapper payrollBatchMapper;
    private final PayrollPaymentService payrollPaymentService;
    private final PayrollProcessManager payrollProcessManager;
    private final SysUserService sysUserService;
    private final UserRoleService userRoleService;
    private final PayCycleService payCycleService;

    @PostMapping
    @SecurityAnnotations.IsFinanceOrAdmin
    @Operation(summary = "创建薪资批次", description = "创建新的薪资批次，默认为草稿状态，仅财务或管理员可操作")
    public ApiResponse<PayrollBatchResponseDto> create(@Valid @RequestBody PayrollBatchCreateRequest req) {
        PayrollBatch b = mapToEntity(req);
        payrollBatchService.save(b);
        return ApiResponse.success(PayrollBatchResponseDto.from(b));
    }

    @PutMapping("/{id}")
    @SecurityAnnotations.IsFinanceOrAdmin
    @Operation(summary = "更新薪资批次", description = "更新批次信息，仅草稿状态可修改，仅财务或管理员可操作")
    public ApiResponse<PayrollBatchResponseDto> update(@PathVariable Long id, @Valid @RequestBody PayrollBatchUpdateRequest req) {
        PayrollBatch b = payrollBatchService.getById(id);
        if (b == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        PayrollBatchStatus status = b.getStatus();
        if (status == null || !status.canEdit()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前状态不可修改");
        }
        updateEntity(b, req);
        payrollBatchService.updateById(b);
        return ApiResponse.success(PayrollBatchResponseDto.from(b));
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
    @Idempotent(key = "'payroll:batch:submit-approval:' + #p0", expireSeconds = 300, message = "薪资批次正在提交审批，请勿重复提交", throwOnLockFail = true, deleteOnError = true)
    @Operation(summary = "提交审批", description = "提交薪资批次进入审批流程")
    public ApiResponse<Boolean> submitApproval(@PathVariable Long id) {
        PayrollBatch batch = payrollBatchService.getById(id);
        if (batch == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        boolean ok = payrollBatchService.submitForApproval(id);
        if (!ok) throw new BusinessException(ErrorCode.INVALID_STATUS, "提交审批失败，请检查批次状态");
        return ApiResponse.success(true);
    }

    @PostMapping("/{id}/dry-run")
    @SecurityAnnotations.IsFinanceOrAdmin
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
            throw new BusinessException(ErrorCode.INVALID_STATUS, "当前状态不可计算，请在锁定、员工确认、异议处理或审批驳回状态下重算");
        }
        boolean ok = payrollProcessManager.computeAndInitialize(id);
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
        Long effectiveManagerId = resolveManagerReviewScope(currentUser(), managerId);
        return ApiResponse.success(calculationService.managerReview(id, department, effectiveManagerId, keyword));
    }

    @GetMapping
    @SecurityAnnotations.IsFinanceOrAdmin
    @Operation(summary = "分页查询批次", description = "支持多条件筛选和分页，返回汇总数据")
    public ApiResponse<PageResponse<PayrollBatchSummaryVO>> list(@RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "10") int size,
                                                                 @RequestParam(required = false) String type,
                                                                 @RequestParam(required = false) String periodLabel,
                                                                 @RequestParam(required = false) String status) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        int offset = (safePage - 1) * safeSize;
        String normalizedType = normalizePayrollTypeFilter(type);
        String normalizedStatus = normalizePayrollBatchStatusFilter(status);
        List<PayrollBatchSummaryVO> records = payrollBatchMapper.selectBatchSummaryList(
                normalizedType,
                periodLabel,
                normalizedStatus,
                offset,
                safeSize
        );
        long total = payrollBatchMapper.countBatchSummary(normalizedType, periodLabel, normalizedStatus);
        return ApiResponse.success(PageResponse.of(records, safePage, safeSize, total));
    }

    @GetMapping("/{id}")
    @SecurityAnnotations.IsFinanceOrAdmin
    @Operation(summary = "获取批次详情", description = "根据ID获取批次详细信息")
    public ApiResponse<PayrollBatchResponseDto> get(@PathVariable Long id) {
        PayrollBatch batch = payrollBatchService.getById(id);
        if (batch == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        }
        return ApiResponse.success(PayrollBatchResponseDto.from(batch));
    }

    @PostMapping("/{id}/retry-payment")
    @SecurityAnnotations.IsFinanceOrAdmin
    @Idempotent(key = "'payroll:batch:retry-payment:' + #p0", expireSeconds = 600, message = "支付重试正在处理中，请勿重复提交", throwOnLockFail = true, deleteOnError = true)
    @Operation(summary = "重试支付", description = "对支付失败批次重置失败记录并重新发放")
    public ApiResponse<PaymentBatchResponse> retryPayment(@PathVariable Long id,
                                                          @RequestParam(defaultValue = "true") boolean triggerTransfer) {
        PayrollBatch batch = payrollBatchService.getById(id);
        if (batch == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        }
        PaymentBatch paymentBatch = payrollPaymentService.retryFailedPayment(id, triggerTransfer);
        return ApiResponse.success(PaymentBatchResponse.from(paymentBatch));
    }

    private PayrollBatch mapToEntity(PayrollBatchCreateRequest req) {
        PayCycle payCycle = resolveOpenPayCycle(req.getPayCycleId());
        PayrollBatch b = new PayrollBatch();
        b.setPayCycleId(req.getPayCycleId());
        b.setPeriodLabel(resolvePeriodLabel(req.getPeriodLabel(), payCycle));
        b.setType(normalizePayrollType(req.getType(), PayrollType.FULL_TIME.getCode()));
        b.setScopeJson(req.getScopeJson());
        b.setCurrency(req.getCurrency() != null ? req.getCurrency() : "CNY");
        b.setConfirmationRequired(req.getConfirmationRequired() == null ? Boolean.TRUE : req.getConfirmationRequired());
        b.setConfirmationMode(req.getConfirmationMode() != null
                ? PayrollConfirmationMode.fromCode(req.getConfirmationMode()).getCode()
                : PayrollConfirmationMode.INDIVIDUAL.getCode());
        b.setStatus(PayrollBatchStatus.DRAFT);
        b.setCalculationStatus(PayrollCalculationStatus.DRAFT);
        b.setBatchRevision(1);
        b.setRemark(req.getRemark());
        return b;
    }

    private void updateEntity(PayrollBatch b, PayrollBatchUpdateRequest req) {
        PayCycle payCycle = req.getPayCycleId() != null
                ? resolveOpenPayCycle(req.getPayCycleId())
                : resolveExistingPayCycle(b.getPayCycleId());
        if (req.getPayCycleId() != null) b.setPayCycleId(req.getPayCycleId());
        if (req.getPeriodLabel() != null || req.getPayCycleId() != null) {
            b.setPeriodLabel(resolvePeriodLabel(req.getPeriodLabel() != null ? req.getPeriodLabel() : b.getPeriodLabel(), payCycle));
        }
        if (req.getScopeJson() != null) b.setScopeJson(req.getScopeJson());
        if (req.getCurrency() != null) b.setCurrency(req.getCurrency());
        if (req.getConfirmationRequired() != null) b.setConfirmationRequired(req.getConfirmationRequired());
        if (req.getConfirmationMode() != null) {
            b.setConfirmationMode(PayrollConfirmationMode.fromCode(req.getConfirmationMode()).getCode());
        }
        if (req.getRemark() != null) b.setRemark(req.getRemark());
    }

    private PayCycle resolveOpenPayCycle(Long payCycleId) {
        if (payCycleId == null) {
            return null;
        }
        PayCycle cycle = payCycleService.getById(payCycleId);
        if (cycle == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "薪酬周期不存在");
        }
        if (!"open".equalsIgnoreCase(cycle.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "只能引用开放状态的薪酬周期");
        }
        return cycle;
    }

    private PayCycle resolveExistingPayCycle(Long payCycleId) {
        if (payCycleId == null) {
            return null;
        }
        PayCycle cycle = payCycleService.getById(payCycleId);
        if (cycle == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "薪酬周期不存在");
        }
        return cycle;
    }

    private String normalizePayrollTypeFilter(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        return normalizePayrollType(type, null);
    }

    private String normalizePayrollType(String type, String defaultType) {
        if (!StringUtils.hasText(type)) {
            return defaultType;
        }
        PayrollType payrollType = PayrollType.fromCode(type.trim());
        if (payrollType == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的薪资类型: " + type);
        }
        return payrollType.getCode();
    }

    private String normalizePayrollBatchStatusFilter(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        PayrollBatchStatus batchStatus = PayrollBatchStatus.fromCode(status.trim());
        if (batchStatus == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的批次状态: " + status);
        }
        return batchStatus.getCode();
    }

    private int safePage(int page) {
        return page < 1 ? 1 : page;
    }

    private int safeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String resolvePeriodLabel(String requestedPeriodLabel, PayCycle payCycle) {
        String periodLabel = StringUtils.hasText(requestedPeriodLabel)
                ? requestedPeriodLabel.trim()
                : null;
        if (payCycle == null) {
            return periodLabel;
        }
        String cyclePeriodLabel = payCycle.getPeriodLabel();
        if (!StringUtils.hasText(cyclePeriodLabel)) {
            return periodLabel;
        }
        if (periodLabel == null) {
            return cyclePeriodLabel.trim();
        }
        if (!periodLabel.equals(cyclePeriodLabel.trim())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "批次周期标签必须与薪酬周期一致");
        }
        return periodLabel;
    }

    private Long resolveManagerReviewScope(SysUser currentUser, Long requestedManagerId) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或用户不存在");
        }
        if (hasAnyRole(currentUser, SecurityConstants.ROLE_ADMIN, SecurityConstants.ROLE_FINANCE)) {
            return requestedManagerId;
        }
        if (currentUser.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "经理账号未绑定员工，无法查看主管审核视图");
        }
        return currentUser.getEmployeeId();
    }

    private SysUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !StringUtils.hasText(authentication.getName())) {
            return null;
        }
        return sysUserService.findByUsername(authentication.getName());
    }

    private boolean hasAnyRole(SysUser user, String... roleCodes) {
        if (user == null || user.getId() == null || roleCodes == null) {
            return false;
        }
        return userRoleService.hasAnyRole(user.getId(), roleCodes);
    }
}
