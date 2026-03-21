package com.yiyundao.compensation.modules.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 支付主动对账任务：周期性轮询处理中记录状态，收敛批次终态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private final SettlementService settlementService;

    @Value("${payment.reconciliation.enabled:true}")
    private boolean enabled;

    @Value("${payment.reconciliation.batch-limit:20}")
    private int batchLimit;

    @Value("${payment.reconciliation.record-limit-per-batch:200}")
    private int recordLimitPerBatch;

    @Scheduled(
            fixedDelayString = "${payment.reconciliation.fixed-delay-ms:300000}",
            initialDelayString = "${payment.reconciliation.initial-delay-ms:120000}"
    )
    public void reconcileInFlightPayments() {
        if (!enabled) {
            return;
        }
        try {
            int scannedBatches = settlementService.reconcileProcessingBatches(batchLimit, recordLimitPerBatch);
            if (scannedBatches > 0) {
                log.info("支付主动对账完成: scannedBatches={}, batchLimit={}, recordLimitPerBatch={}",
                        scannedBatches, batchLimit, recordLimitPerBatch);
            }
        } catch (Exception ex) {
            log.error("支付主动对账任务异常", ex);
        }
    }
}
