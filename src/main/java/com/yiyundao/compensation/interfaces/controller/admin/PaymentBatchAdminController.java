package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.yiyundao.compensation.security.SecurityAnnotations;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/payment/batch")
@RequiredArgsConstructor
@SecurityAnnotations.IsAdmin
public class PaymentBatchAdminController {

    private final PaymentBatchService paymentBatchService;
    private final PaymentRecordService paymentRecordService;

    // 创建批次（草稿）
    @PostMapping
    public ApiResponse<PaymentBatch> create(@RequestBody CreateBatchForm form) {
        PaymentBatch b = new PaymentBatch();
        b.setBatchNo(form.getBatchNo());
        b.setBatchName(form.getBatchName());
        b.setPaymentType(form.getPaymentType());
        b.setTotalAmount(form.getTotalAmount());
        b.setTotalCount(form.getTotalCount());
        b.setStatus(BatchStatus.DRAFT);
        b.setSubmitTime(LocalDateTime.now());
        paymentBatchService.save(b);
        return ApiResponse.success(b);
    }

    // 取消/关闭批次（将状态设为 FAILED）
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        paymentBatchService.updateStatus(id, BatchStatus.FAILED);
        return ApiResponse.success(null);
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

