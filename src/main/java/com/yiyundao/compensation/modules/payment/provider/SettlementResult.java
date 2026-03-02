package com.yiyundao.compensation.modules.payment.provider;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class SettlementResult {

    private boolean success;
    private String providerOrderNo;
    private String providerTradeNo;
    private SettlementStatus status;
    private String errorCode;
    private String errorMsg;
    private LocalDateTime responseTime;
    private Map<String, Object> metadata;
}

