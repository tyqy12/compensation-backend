package com.yiyundao.compensation.modules.payment.service;

import com.yiyundao.compensation.modules.payment.dto.PaymentBatchTransferValidationDto;
import com.yiyundao.compensation.modules.payment.provider.SettlementCallbackResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementStatus;

import java.util.Map;

/**
 * 统一结算服务入口，负责按渠道路由执行。
 */
public interface SettlementService {

    SettlementResult singleTransfer(Long paymentRecordId);

    SettlementResult retryFailedRecord(Long paymentRecordId);

    PaymentBatchTransferValidationDto validateBatchForTransfer(String batchNo, boolean persistFailure);

    void batchTransfer(String batchNo);

    SettlementStatus queryStatus(String providerCode, String providerOrderNo);

    SettlementCallbackResult handleCallback(String providerCode, Map<String, String> params);

    /**
     * 主动轮询对账处理中批次，尝试收敛记录与批次状态。
     *
     * @param batchLimit          本次最多处理批次数
     * @param recordLimitPerBatch 每个批次最多处理记录数
     * @return 本次实际扫描的批次数
     */
    int reconcileProcessingBatches(int batchLimit, int recordLimitPerBatch);
}
