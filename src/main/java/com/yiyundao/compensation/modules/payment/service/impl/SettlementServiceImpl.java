package com.yiyundao.compensation.modules.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.modules.payment.provider.SettlementCallbackResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementProvider;
import com.yiyundao.compensation.modules.payment.provider.SettlementRequest;
import com.yiyundao.compensation.modules.payment.provider.SettlementResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementStatus;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementProviderRoutingService;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final List<SettlementProvider> providers;
    private final PaymentRecordService paymentRecordService;
    private final PaymentBatchService paymentBatchService;
    private final NotificationService notificationService;
    private final PayrollBatchMapper payrollBatchMapper;
    private final SettlementProviderRoutingService routingService;
    private final EmployeeService employeeService;
    private final PayrollBatchService payrollBatchService;

    private Map<String, SettlementProvider> providerMap;

    @PostConstruct
    public void initProviderMap() {
        Map<String, SettlementProvider> mapped = new LinkedHashMap<>();
        for (SettlementProvider provider : providers) {
            String code = normalizeProviderCode(provider.getProviderCode());
            if (!StringUtils.hasText(code)) {
                continue;
            }
            SettlementProvider previous = mapped.putIfAbsent(code, provider);
            if (previous != null) {
                throw new IllegalStateException("Duplicate settlement provider code: " + code);
            }
        }
        this.providerMap = mapped;
        log.info("Settlement provider initialized: {}", providerMap.keySet());
    }

    @Override
    public SettlementResult singleTransfer(Long paymentRecordId) {
        PaymentRecord record = paymentRecordService.getById(paymentRecordId);
        if (record == null) {
            throw new IllegalArgumentException("支付记录不存在: " + paymentRecordId);
        }

        String providerCode = resolveProviderCode(record);
        SettlementProvider provider = getProvider(providerCode);
        SettlementRequest request = buildRequest(record);
        SettlementResult result = provider.singleTransfer(request);
        persistProviderResult(record.getId(), providerCode, result);
        return result;
    }

    @Override
    @Async
    public void batchTransfer(String batchNo) {
        if (!StringUtils.hasText(batchNo)) {
            throw new IllegalArgumentException("批次号不能为空");
        }
        PaymentBatch batch = paymentBatchService.getByBatchNo(batchNo);
        if (batch == null) {
            throw new IllegalArgumentException("支付批次不存在: " + batchNo);
        }

        BatchStatus previousStatus = batch.getStatus();
        batch.setStatus(BatchStatus.PROCESSING);
        batch.setProcessStartTime(LocalDateTime.now());
        paymentBatchService.updateById(batch);

        List<PaymentRecord> pendingRecords = paymentRecordService.getByBatchNo(batchNo, PaymentStatus.PENDING);
        if (!pendingRecords.isEmpty()) {
            for (PaymentRecord record : pendingRecords) {
                if (record == null || record.getId() == null) {
                    continue;
                }
                try {
                    SettlementResult result = singleTransfer(record.getId());
                    if (result == null || !result.isSuccess()) {
                        log.warn("批次转账提交失败: batchNo={}, recordId={}, provider={}, error={}",
                                batchNo,
                                record.getId(),
                                record.getProviderCode(),
                                result == null ? "empty_result" : result.getErrorMsg());
                    }
                } catch (Exception ex) {
                    log.error("批次转账执行异常: batchNo={}, recordId={}", batchNo, record.getId(), ex);
                }
            }
        }

        settleBatchStatus(batch, previousStatus);
    }

    @Override
    public SettlementStatus queryStatus(String providerCode, String providerOrderNo) {
        SettlementProvider provider = getProvider(providerCode);
        return provider.queryStatus(providerOrderNo);
    }

    @Override
    public SettlementCallbackResult handleCallback(String providerCode, Map<String, String> params) {
        SettlementProvider provider = getProvider(providerCode);
        if (!provider.verifyCallback(params)) {
            return SettlementCallbackResult.builder()
                    .success(false)
                    .errorMsg("回调验签失败")
                    .build();
        }
        SettlementCallbackResult callbackResult = provider.handleCallback(params);
        refreshBatchStatusFromCallback(providerCode, callbackResult);
        return callbackResult;
    }

    private SettlementProvider getProvider(String providerCode) {
        String normalized = normalizeProviderCode(providerCode);
        SettlementProvider provider = providerMap.get(normalized);
        if (provider == null) {
            throw new IllegalArgumentException("不支持的结算渠道: " + providerCode);
        }
        return provider;
    }

    private String resolveProviderCode(PaymentRecord record) {
        // 如果记录已有渠道代码，直接使用
        if (StringUtils.hasText(record.getProviderCode())) {
            return normalizeProviderCode(record.getProviderCode());
        }

        // 使用新的路由逻辑
        try {
            // 获取员工信息
            Employee employee = employeeService.getById(record.getEmployeeId());
            if (employee == null) {
                log.warn("员工不存在，使用默认渠道: employeeId={}", record.getEmployeeId());
                return "alipay";
            }

            // 获取薪酬批次信息（如果有）
            PayrollBatch payrollBatch = null;
            if (StringUtils.hasText(record.getBatchNo())) {
                payrollBatch = payrollBatchService.lambdaQuery()
                    .eq(PayrollBatch::getPaymentBatchNo, record.getBatchNo())
                    .one();
            }

            // 使用路由服务确定渠道
            String providerCode = routingService.determineProvider(employee, payrollBatch);
            log.debug("路由确定渠道: employeeId={}, provider={}", employee.getId(), providerCode);
            return normalizeProviderCode(providerCode);

        } catch (Exception e) {
            log.error("路由确定渠道失败，使用默认渠道: recordId={}", record.getId(), e);
            // 兜底逻辑：使用 paymentMethod 或默认 alipay
            if (StringUtils.hasText(record.getPaymentMethod())) {
                return normalizeProviderCode(record.getPaymentMethod());
            }
            return "alipay";
        }
    }

    private String normalizeProviderCode(String providerCode) {
        if (!StringUtils.hasText(providerCode)) {
            return "";
        }
        return providerCode.trim().toLowerCase();
    }

    private SettlementRequest buildRequest(PaymentRecord record) {
        return SettlementRequest.builder()
                .paymentRecordId(record.getId())
                .bizNo(StringUtils.hasText(record.getProviderOrderNo()) ? record.getProviderOrderNo() : record.getAlipayOrderNo())
                .amount(record.getAmount())
                .currency(record.getCurrency())
                .recipientName(record.getRecipientName())
                .recipientAccount(record.getRecipientAccount())
                .remark(record.getPaymentDesc())
                .build();
    }

    private void refreshBatchStatusFromCallback(String providerCode, SettlementCallbackResult callbackResult) {
        if (callbackResult == null || !StringUtils.hasText(callbackResult.getBizNo())) {
            return;
        }
        String normalizedProviderCode = normalizeProviderCode(providerCode);
        PaymentRecord record = paymentRecordService.getByProviderOrderNo(normalizedProviderCode, callbackResult.getBizNo());
        if (record == null || !StringUtils.hasText(record.getBatchNo())) {
            return;
        }
        PaymentBatch batch = paymentBatchService.getByBatchNo(record.getBatchNo());
        if (batch == null) {
            return;
        }
        settleBatchStatus(batch, batch.getStatus());
    }

    private void settleBatchStatus(PaymentBatch batch, BatchStatus previousStatus) {
        List<PaymentRecord> allRecords = paymentRecordService.getByBatchNo(batch.getBatchNo(), null);
        if (allRecords.isEmpty()) {
            batch.setSuccessCount(0);
            batch.setFailedCount(0);
            batch.setStatus(BatchStatus.FAILED);
            batch.setProcessEndTime(LocalDateTime.now());
            paymentBatchService.updateById(batch);
            if (previousStatus != BatchStatus.FAILED) {
                syncPayrollBatchStatus(batch);
                safeNotifyBatchCompleted(batch);
            }
            return;
        }

        int successCount = 0;
        int failedCount = 0;
        int processingCount = 0;
        for (PaymentRecord record : allRecords) {
            PaymentStatus status = record.getStatus();
            if (status == PaymentStatus.SUCCESS) {
                successCount++;
            } else if (status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED) {
                failedCount++;
            } else {
                processingCount++;
            }
        }

        BatchStatus targetStatus;
        if (processingCount > 0) {
            targetStatus = BatchStatus.PROCESSING;
            batch.setProcessEndTime(null);
        } else if (failedCount == 0) {
            targetStatus = BatchStatus.COMPLETED;
            batch.setProcessEndTime(LocalDateTime.now());
        } else if (successCount == 0) {
            targetStatus = BatchStatus.FAILED;
            batch.setProcessEndTime(LocalDateTime.now());
        } else {
            targetStatus = BatchStatus.COMPLETED;
            batch.setProcessEndTime(LocalDateTime.now());
        }

        batch.setSuccessCount(successCount);
        batch.setFailedCount(failedCount);
        batch.setStatus(targetStatus);
        paymentBatchService.updateById(batch);

        if (isTerminalStatus(targetStatus) && previousStatus != targetStatus) {
            syncPayrollBatchStatus(batch);
            safeNotifyBatchCompleted(batch);
        }
    }

    private boolean isTerminalStatus(BatchStatus status) {
        return status == BatchStatus.COMPLETED || status == BatchStatus.FAILED;
    }

    private void safeNotifyBatchCompleted(PaymentBatch batch) {
        try {
            notificationService.sendBatchCompleteNotification(batch);
        } catch (Exception ex) {
            log.warn("批次完成通知发送失败: batchNo={}, msg={}", batch.getBatchNo(), ex.getMessage());
        }
    }

    private void syncPayrollBatchStatus(PaymentBatch paymentBatch) {
        if (paymentBatch == null || !StringUtils.hasText(paymentBatch.getBatchNo())) {
            return;
        }
        BatchStatus status = paymentBatch.getStatus();
        if (status != BatchStatus.COMPLETED && status != BatchStatus.FAILED) {
            return;
        }
        LambdaUpdateWrapper<PayrollBatch> wrapper = new LambdaUpdateWrapper<PayrollBatch>()
                .eq(PayrollBatch::getPaymentBatchNo, paymentBatch.getBatchNo())
                .set(PayrollBatch::getStatus, status == BatchStatus.COMPLETED ? "paid" : "pay_failed");
        payrollBatchMapper.update(null, wrapper);
    }

    private void persistProviderResult(Long recordId, String providerCode, SettlementResult result) {
        if (recordId == null || result == null) {
            return;
        }
        PaymentRecord update = new PaymentRecord();
        update.setId(recordId);
        update.setProviderCode(providerCode);
        if (StringUtils.hasText(result.getProviderOrderNo())) {
            update.setProviderOrderNo(result.getProviderOrderNo());
        }
        if (StringUtils.hasText(result.getProviderTradeNo())) {
            update.setProviderTradeNo(result.getProviderTradeNo());
        }
        paymentRecordService.updateById(update);
    }
}
