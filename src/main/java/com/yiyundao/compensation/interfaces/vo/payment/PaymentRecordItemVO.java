package com.yiyundao.compensation.interfaces.vo.payment;

import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentRecordItemVO {
    private Long id;
    private String batchNo;
    private String paymentType;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String alipayOrderNo;
    private String alipayTradeNo;
    private String providerCode;
    private String providerOrderNo;
    private String providerTradeNo;
    private String errorCode;
    private String errorMsg;
    private LocalDateTime paymentTime;
    private LocalDateTime notificationTime;

    public static PaymentRecordItemVO from(PaymentRecord r) {
        PaymentRecordItemVO vo = new PaymentRecordItemVO();
        vo.setId(r.getId());
        vo.setBatchNo(r.getBatchNo());
        vo.setPaymentType(r.getPaymentType() != null ? r.getPaymentType().getCode() : null);
        vo.setAmount(r.getAmount());
        vo.setCurrency(r.getCurrency());
        vo.setStatus(r.getStatus() != null ? r.getStatus().getCode() : null);
        vo.setAlipayOrderNo(r.getAlipayOrderNo());
        vo.setAlipayTradeNo(r.getAlipayTradeNo());
        vo.setProviderCode(r.getProviderCode() != null ? r.getProviderCode() : "alipay");
        vo.setProviderOrderNo(r.getProviderOrderNo() != null ? r.getProviderOrderNo() : r.getAlipayOrderNo());
        vo.setProviderTradeNo(r.getProviderTradeNo() != null ? r.getProviderTradeNo() : r.getAlipayTradeNo());
        vo.setErrorCode(r.getErrorCode());
        vo.setErrorMsg(r.getErrorMsg());
        vo.setPaymentTime(r.getPaymentTime());
        vo.setNotificationTime(r.getNotificationTime());
        return vo;
    }
}
