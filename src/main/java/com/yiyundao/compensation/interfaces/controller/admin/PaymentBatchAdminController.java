package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPaymentFailureResponseDto;
import com.yiyundao.compensation.modules.payment.dto.PaymentBatchResponse;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentFailureService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.yiyundao.compensation.security.SecurityAnnotations;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/payment/batch")
@RequiredArgsConstructor
@SecurityAnnotations.IsAdmin
public class PaymentBatchAdminController {

    private final PaymentBatchService paymentBatchService;
    private final PaymentRecordService paymentRecordService;
    private final PayrollPaymentFailureService payrollPaymentFailureService;

    // 创建批次（草稿）
    @PostMapping
    public ApiResponse<PaymentBatchResponse> create(@RequestBody CreateBatchForm form) {
        PaymentBatch b = new PaymentBatch();
        b.setBatchNo(form.getBatchNo());
        b.setBatchName(form.getBatchName());
        b.setPaymentType(form.getPaymentType());
        b.setTotalAmount(form.getTotalAmount());
        b.setTotalCount(form.getTotalCount());
        b.setStatus(BatchStatus.DRAFT);
        b.setSubmitTime(LocalDateTime.now());
        paymentBatchService.save(b);
        return ApiResponse.success(PaymentBatchResponse.from(b));
    }

    // 取消/关闭批次（将状态设为 FAILED）
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        PaymentBatch batch = paymentBatchService.getById(id);
        if (batch == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "支付批次不存在");
        }
        if (batch.getStatus() == BatchStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "已完成支付批次不允许取消");
        }
        List<PaymentRecord> beforeCancelRecords = paymentRecordService.list(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getBatchNo, batch.getBatchNo()));
        boolean hasInFlightRecord = beforeCancelRecords.stream().anyMatch(this::isInFlightRecord);
        if (hasInFlightRecord) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "存在已提交渠道的支付记录，请等待渠道结果后再取消");
        }
        paymentRecordService.update(new UpdateWrapper<PaymentRecord>()
                .eq("batch_no", batch.getBatchNo())
                .eq("status", PaymentStatus.PENDING.getCode())
                .set("status", PaymentStatus.CANCELLED.getCode())
                .set("error_code", "BATCH_CANCELLED")
                .set("error_msg", "payment batch cancelled by admin"));
        List<PaymentRecord> records = paymentRecordService.list(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getBatchNo, batch.getBatchNo()));
        long successCount = records.stream().filter(record -> record.getStatus() == PaymentStatus.SUCCESS).count();
        long failedCount = records.stream()
                .filter(record -> record.getStatus() == PaymentStatus.FAILED || record.getStatus() == PaymentStatus.CANCELLED)
                .count();
        long processingCount = records.stream()
                .filter(record -> record.getStatus() == PaymentStatus.PENDING || record.getStatus() == PaymentStatus.PROCESSING)
                .count();
        batch.setSuccessCount((int) successCount);
        batch.setFailedCount((int) failedCount);
        if (processingCount > 0) {
            batch.setStatus(BatchStatus.PROCESSING);
            batch.setPaymentStatus(PaymentBatchProcessStatus.PROCESSING);
            batch.setProcessEndTime(null);
        } else if (successCount > 0) {
            batch.setStatus(BatchStatus.COMPLETED);
            batch.setPaymentStatus(PaymentBatchProcessStatus.PARTIAL_SUCCESS);
            batch.setProcessEndTime(LocalDateTime.now());
        } else {
            batch.setStatus(BatchStatus.FAILED);
            batch.setPaymentStatus(PaymentBatchProcessStatus.FAILED);
            batch.setProcessEndTime(LocalDateTime.now());
        }
        paymentBatchService.updateTerminalState(batch);
        return ApiResponse.success(null);
    }

    private boolean isInFlightRecord(PaymentRecord record) {
        if (record == null) {
            return false;
        }
        if (record.getStatus() == PaymentStatus.PROCESSING) {
            return true;
        }
        return (record.getStatus() == PaymentStatus.PENDING)
                && (org.springframework.util.StringUtils.hasText(record.getProviderOrderNo())
                || org.springframework.util.StringUtils.hasText(record.getAlipayOrderNo()));
    }

    // 概览统计：各状态数量 + 今日/本月支付成功金额
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> map = new java.util.HashMap<>();
        EnumMap<BatchStatus, Long> byStatus = new EnumMap<>(BatchStatus.class);
        for (BatchStatus st : BatchStatus.values()) {
            long c = paymentBatchService.count(new LambdaQueryWrapper<PaymentBatch>().eq(PaymentBatch::getStatus, st));
            byStatus.put(st, c);
        }
        map.put("byStatus", byStatus);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        BigDecimal today = paymentRecordService.list(new LambdaQueryWrapper<PaymentRecord>()
                .ge(PaymentRecord::getPaymentTime, todayStart)
                .eq(PaymentRecord::getStatus, com.yiyundao.compensation.enums.PaymentStatus.SUCCESS))
                .stream().map(PaymentRecord::getAmount).filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal month = paymentRecordService.list(new LambdaQueryWrapper<PaymentRecord>()
                .ge(PaymentRecord::getPaymentTime, monthStart)
                .eq(PaymentRecord::getStatus, com.yiyundao.compensation.enums.PaymentStatus.SUCCESS))
                .stream().map(PaymentRecord::getAmount).filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        map.put("todayPaid", today);
        map.put("monthPaid", month);
        return ApiResponse.success(map);
    }

    @GetMapping("/failures")
    public ApiResponse<List<PayrollPaymentFailureResponseDto>> paymentFailures(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.success(payrollPaymentFailureService.listUnresolved(limit).stream()
                .map(PayrollPaymentFailureResponseDto::from)
                .toList());
    }

    @PostMapping("/failures/{id}/retry")
    public ApiResponse<PayrollPaymentFailureResponseDto> retryPaymentFailure(@PathVariable Long id,
                                                                             @RequestParam(defaultValue = "true") boolean triggerTransfer) {
        return ApiResponse.success(PayrollPaymentFailureResponseDto.from(
                payrollPaymentFailureService.retry(id, triggerTransfer)));
    }

    @Data
    public static class CreateBatchForm {
        @NotBlank
        private String batchNo;
        @NotBlank
        private String batchName;
        private com.yiyundao.compensation.enums.PaymentType paymentType;
        private BigDecimal totalAmount;
        private Integer totalCount;
    }
}
