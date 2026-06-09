package com.yiyundao.compensation.interfaces.controller.payment;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.idempotent.Idempotent;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.modules.payment.dto.PaymentBatchResponse;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payment.dto.PaymentBatchTransferValidationDto;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import com.yiyundao.compensation.interfaces.vo.payment.PaymentBatchVO;
import com.yiyundao.compensation.interfaces.vo.payment.PaymentRecordItemVO;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import com.yiyundao.compensation.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/payment/batch")
@SecurityAnnotations.IsFinanceOrAdmin
@RequiredArgsConstructor
public class PaymentBatchController {

    private final PaymentBatchService paymentBatchService;
    private final PaymentRecordService paymentRecordService;
    private final SettlementService settlementService;
    private final AuditLogService auditLogService;
    private final EmployeeMapper employeeMapper;
    private final EncryptionService encryptionService;
    private final PayrollBatchService payrollBatchService;
    private final PayrollPaymentService payrollPaymentService;

    // 分页查询支付批次
    @GetMapping
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "submitTime") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String order
    ) {
        Page<PaymentBatch> p = paymentBatchService.pagePaymentBatches(page, size, keyword, status, paymentType, startDate, endDate, sortBy, order);
        Map<String, Object> result = new HashMap<>();
        result.put("records", p.getRecords().stream().map(PaymentBatchVO::from).toList());
        result.put("total", p.getTotal());
        result.put("current", p.getCurrent());
        result.put("size", p.getSize());
        return ApiResponse.success(result);
    }

    // 获取批次详情
    @GetMapping("/{batchNo}")
    public ApiResponse<PaymentBatchVO> detail(@PathVariable String batchNo) {
        PaymentBatch batch = paymentBatchService.getByBatchNo(batchNo);
        if (batch == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        }
        return ApiResponse.success(PaymentBatchVO.from(batch));
    }

    // 获取批次记录列表，可按状态过滤
    @GetMapping("/{batchNo}/records")
    public ApiResponse<List<PaymentRecordItemVO>> records(@PathVariable String batchNo,
                                                          @RequestParam(required = false) String status) {
        if (paymentBatchService.getByBatchNo(batchNo) == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        }
        PaymentStatus st = null;
        if (org.springframework.util.StringUtils.hasText(status)) {
            try {
                st = PaymentStatus.fromCode(status.trim());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的支付状态: " + status);
            }
        }
        List<PaymentRecord> list = paymentRecordService.getByBatchNo(batchNo, st);
        Set<Long> employeeIds = list.stream()
                .map(PaymentRecord::getEmployeeId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, Employee> employeeMap = employeeIds.isEmpty()
                ? Map.of()
                : employeeMapper.selectBatchIds(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, employee -> employee));
        return ApiResponse.success(list.stream()
                .map(record -> PaymentRecordItemVO.from(record, employeeMap.get(record.getEmployeeId()), encryptionService))
                .collect(Collectors.toList()));
    }

    // 启动批量转账（异步）
    @PostMapping("/{batchNo}/start")
    @Idempotent(key = "'payment:batch:start:' + #p0", expireSeconds = 600, message = "批量转账正在处理中，请勿重复提交", throwOnLockFail = true, deleteOnError = true)
    public ApiResponse<String> start(@PathVariable String batchNo) {
        long begin = System.currentTimeMillis();
        try {
            PaymentBatch batch = paymentBatchService.getByBatchNo(batchNo);
            if (batch == null) {
                audit("启动批量转账", null, batchNo, "PAYMENT", false, "批次不存在", begin);
                return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
            }
            if (batch.getStatus() != BatchStatus.SUBMITTED && batch.getStatus() != BatchStatus.APPROVED) {
                String failMsg = "当前支付批次状态不可启动转账: " + batch.getStatus();
                audit("启动批量转账", null, batchNo, "PAYMENT", false, failMsg, begin);
                return ApiResponse.error(ErrorCode.INVALID_STATUS, failMsg);
            }

            PaymentBatchTransferValidationDto validation = settlementService.validateBatchForTransfer(batchNo, true);
            if (!Boolean.TRUE.equals(validation.getPass())) {
                String failMsg = buildTransferValidationFailureMessage(validation);
                audit("启动批量转账", null, batchNo, "PAYMENT", false, failMsg, begin);
                return ApiResponse.error(
                        ErrorCode.BUSINESS_ERROR.getCode(),
                        failMsg,
                        Map.of("validation", validation)
                );
            }

            settlementService.batchTransfer(batchNo);
            audit("启动批量转账", null, batchNo, "PAYMENT", true,
                    "amount=" + batch.getTotalAmount() + ",count=" + batch.getTotalCount(), begin);
            return ApiResponse.success("批量转账已启动");
        } catch (Exception e) {
            audit("启动批量转账", null, batchNo, "PAYMENT", false, e.getMessage(), begin);
            return ApiResponse.error("启动失败: " + e.getMessage());
        }
    }

    private String buildTransferValidationFailureMessage(PaymentBatchTransferValidationDto validation) {
        if (validation != null && validation.getWarnings() != null && !validation.getWarnings().isEmpty()) {
            return "批次校验未通过：" + String.join("；", validation.getWarnings());
        }
        int blockedCount = validation == null || validation.getBlockedCount() == null ? 0 : validation.getBlockedCount();
        return String.format("批次校验未通过：%d条风险记录，请先修复收款信息后再重试", blockedCount);
    }

    @GetMapping("/{batchNo}/precheck")
    public ApiResponse<PaymentBatchTransferValidationDto> precheck(@PathVariable String batchNo,
                                                                   @RequestParam(defaultValue = "false") boolean persistFailure) {
        if (paymentBatchService.getByBatchNo(batchNo) == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        }
        return ApiResponse.success(settlementService.validateBatchForTransfer(batchNo, persistFailure));
    }

    @PostMapping("/{batchNo}/retry-failed")
    @Idempotent(key = "'payment:batch:retry-failed:' + #p0", expireSeconds = 600, message = "支付重试正在处理中，请勿重复提交", throwOnLockFail = true, deleteOnError = true)
    public ApiResponse<PaymentBatchResponse> retryFailed(@PathVariable String batchNo,
                                                         @RequestParam(defaultValue = "true") boolean triggerTransfer) {
        if (paymentBatchService.getByBatchNo(batchNo) == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "批次不存在");
        }
        PayrollBatch payrollBatch = payrollBatchService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayrollBatch>()
                        .eq(PayrollBatch::getPaymentBatchNo, batchNo)
                        .last("limit 1")
        );
        if (payrollBatch == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "未找到关联薪资批次");
        }
        PaymentBatch retriedBatch = payrollPaymentService.retryFailedPayment(payrollBatch.getId(), triggerTransfer);
        return ApiResponse.success(PaymentBatchResponse.from(retriedBatch));
    }

    private void audit(String operation, String username, String batchNo, String businessType, boolean success, String detail, long begin) {
        try {
            auditLogService.record(
                    operation,
                    "POST",
                    "/payment/batch/" + batchNo + "/start",
                    null,
                    null,
                    businessType,
                    batchNo,
                    username,
                    detail,
                    success ? "OK" : "FAILED",
                    success ? null : detail,
                    System.currentTimeMillis() - begin
            );
        } catch (Exception e) {
            log.warn("支付审计记录失败: {}", e.getMessage());
        }
    }
}
