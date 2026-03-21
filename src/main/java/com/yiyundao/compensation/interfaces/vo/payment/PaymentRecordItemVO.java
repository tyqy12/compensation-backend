package com.yiyundao.compensation.interfaces.vo.payment;

import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.service.EncryptionService;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentRecordItemVO {
    private Long id;
    private String batchNo;
    private Long employeeId;
    private String employeeNo;
    private String employeeName;
    private String paymentType;
    private String paymentMethod;
    private BigDecimal amount;
    private String currency;
    private String recipientName;
    private String recipientAccountMasked;
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static PaymentRecordItemVO from(PaymentRecord r) {
        return from(r, null, null);
    }

    public static PaymentRecordItemVO from(PaymentRecord r, Employee employee, EncryptionService encryptionService) {
        PaymentRecordItemVO vo = new PaymentRecordItemVO();
        vo.setId(r.getId());
        vo.setBatchNo(r.getBatchNo());
        vo.setEmployeeId(r.getEmployeeId());
        if (employee != null) {
            vo.setEmployeeNo(employee.getEmployeeId());
            vo.setEmployeeName(employee.getName());
        }
        vo.setPaymentType(r.getPaymentType() != null ? r.getPaymentType().getCode() : null);
        vo.setPaymentMethod(r.getPaymentMethod());
        vo.setAmount(r.getAmount());
        vo.setCurrency(r.getCurrency());
        vo.setRecipientName(r.getRecipientName());
        vo.setRecipientAccountMasked(maskRecipientAccount(r.getRecipientAccount(), r.getPaymentMethod(), encryptionService));
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
        vo.setCreateTime(r.getCreateTime());
        vo.setUpdateTime(r.getUpdateTime());
        return vo;
    }

    private static String maskRecipientAccount(String recipientAccount, String paymentMethod, EncryptionService encryptionService) {
        if (!StringUtils.hasText(recipientAccount)) {
            return null;
        }

        String account = recipientAccount.trim();
        String normalizedMethod = StringUtils.hasText(paymentMethod) ? paymentMethod.trim().toUpperCase() : "";

        if ("BANK_CARD".equals(normalizedMethod)) {
            String compact = account.replaceAll("\\s+", "");
            if (encryptionService != null) {
                return encryptionService.maskBankAccount(compact);
            }
            return maskCommon(compact, 4, 4);
        }

        if (account.contains("@")) {
            return maskEmail(account);
        }

        String compactDigits = account.replaceAll("\\s+", "");
        if (compactDigits.matches("\\d{7,}")) {
            if (encryptionService != null) {
                return encryptionService.maskPhone(compactDigits);
            }
            return maskCommon(compactDigits, 3, 4);
        }

        return maskCommon(account, 2, 2);
    }

    private static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "****";
        }
        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);
        if (localPart.length() <= 2) {
            return localPart.substring(0, 1) + "***" + domainPart;
        }
        return localPart.substring(0, 2) + "***" + domainPart;
    }

    private static String maskCommon(String value, int prefixVisible, int suffixVisible) {
        if (!StringUtils.hasText(value)) {
            return "****";
        }
        String normalized = value.trim();
        if (normalized.length() <= 2) {
            return "****";
        }
        if (normalized.length() <= prefixVisible + suffixVisible) {
            return normalized.substring(0, 1) + "**" + normalized.substring(normalized.length() - 1);
        }
        return normalized.substring(0, prefixVisible) + "****" + normalized.substring(normalized.length() - suffixVisible);
    }
}
