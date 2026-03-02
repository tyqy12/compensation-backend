package com.yiyundao.compensation.modules.payment.provider;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class SettlementCallbackResult {

    private boolean success;
    private String bizNo;
    private SettlementStatus status;
    private String errorMsg;
    private Map<String, Object> metadata;
}

