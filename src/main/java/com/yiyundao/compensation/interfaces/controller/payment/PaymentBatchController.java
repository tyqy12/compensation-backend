package com.yiyundao.compensation.interfaces.controller.payment;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payment.dto.PaymentBatchTransferValidationDto;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.interfaces.vo.payment.PaymentBatchVO;
import com.yiyundao.compensation.interfaces.vo.payment.PaymentRecordItemVO;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
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
@RequiredArgsConstructor
public class PaymentBatchController {

    private final PaymentBatchService paymentBatchService;
    private final PaymentRecordService paymentRecordService;
    private final SettlementService settlementService;
    private final AuditLogService auditLogService;
    private final EmployeeMapper employeeMapper;
    private final EncryptionService encryptionService;

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
        return ApiResponse.success(batch == null ? null : PaymentBatchVO.from(batch));
    }

    // 获取批次记录列表，可按状态过滤
    @GetMapping("/{batchNo}/records")
    public ApiResponse<List<PaymentRecordItemVO>> records(@PathVariable String batchNo,
                                                          @RequestParam(required = false) String status) {
        PaymentStatus st = null;
        if (status != null) {
            st = PaymentStatus.fromCode(status);
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
    public ApiResponse<String> start(@PathVariable String batchNo) {
        long begin = System.currentTimeMillis();
        try {
            PaymentBatch batch = paymentBatchService.getByBatchNo(batchNo);
            if (batch == null) {
                audit("启动批量转账", null, batchNo, "PAYMENT", false, "批次不存在", begin);
                return ApiResponse.error("批次不存在");
            }

            PaymentBatchTransferValidationDto validation = settlementService.validateBatchForTransfer(batchNo, true);
            if (!Boolean.TRUE.equals(validation.getPass())) {
                String failMsg = String.format("批次校验未通过：%d条风险记录，请先修复收款信息后再重试",
                        validation.getBlockedCount() == null ? 0 : validation.getBlockedCount());
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

    @GetMapping("/{batchNo}/precheck")
    public ApiResponse<PaymentBatchTransferValidationDto> precheck(@PathVariable String batchNo,
                                                                   @RequestParam(defaultValue = "true") boolean persistFailure) {
        return ApiResponse.success(settlementService.validateBatchForTransfer(batchNo, persistFailure));
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
