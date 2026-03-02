package com.yiyundao.compensation.modules.payment.service;

import com.yiyundao.compensation.modules.payment.provider.SettlementCallbackResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementResult;
import com.yiyundao.compensation.modules.payment.provider.SettlementStatus;

import java.util.Map;

/**
 * 统一结算服务入口，负责按渠道路由执行。
 */
public interface SettlementService {

    SettlementResult singleTransfer(Long paymentRecordId);

    void batchTransfer(String batchNo);

    SettlementStatus queryStatus(String providerCode, String providerOrderNo);

    SettlementCallbackResult handleCallback(String providerCode, Map<String, String> params);
}
