package com.yiyundao.compensation.modules.payment.provider;

import com.yiyundao.compensation.enums.PayrollType;

import java.util.List;
import java.util.Map;

/**
 * 统一结算渠道接口。
 */
public interface SettlementProvider {

    String getProviderCode();

    String getProviderName();

    SettlementResult singleTransfer(SettlementRequest request);

    SettlementResult batchTransfer(List<SettlementRequest> requests);

    SettlementStatus queryStatus(String providerOrderNo);

    SettlementCallbackResult handleCallback(Map<String, String> params);

    boolean verifyCallback(Map<String, String> params);

    boolean healthCheck();

    boolean supports(PayrollType payrollType);
}

