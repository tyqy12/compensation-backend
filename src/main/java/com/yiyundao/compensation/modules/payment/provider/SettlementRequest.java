package com.yiyundao.compensation.modules.payment.provider;

import com.yiyundao.compensation.enums.PayrollType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class SettlementRequest {

    private Long paymentRecordId;
    private String bizNo;

    private BigDecimal amount;
    private String currency;

    private String recipientName;
    private String recipientAccount;
    private String recipientIdType;
    private String recipientIdNo;

    private String remark;
    private PayrollType payrollType;

    private Map<String, Object> extra;
}

