package com.yiyundao.compensation.modules.payroll.support;

import com.yiyundao.compensation.common.utils.ValidationUtils;
import com.yiyundao.compensation.common.utils.SettlementAccountPlaintextGuard;
import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.enums.SettlementAccountType;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payment.support.SettlementRouteProviderResolver;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollDistributionRoutingSupport {

    private final EncryptionService encryptionService;

    public RouteSnapshot buildSnapshot(PayrollBatch batch, PayrollLine line, Employee employee) {
        if (line == null || employee == null) {
            return RouteSnapshot.failed("员工信息不存在");
        }

        String employmentType = normalizeEmploymentType(resolveEmploymentType(batch, line, employee));
        if (!StringUtils.hasText(employmentType)) {
            return RouteSnapshot.failed("缺少用工类型，无法路由结算渠道");
        }

        String settlementType = normalizeSettlementType(employee.getSettlementAccountType());
        AccountResolution settlementAccountResult = decryptAccount(employee.getSettlementAccount());
        if (settlementAccountResult.decryptFailed()) {
            return RouteSnapshot.failed("收款账号解密失败，请重新维护收款信息");
        }
        String settlementAccount = settlementAccountResult.value();
        boolean hasConfiguredAccount = settlementAccountResult.configured();

        if (!StringUtils.hasText(settlementAccount)) {
            AccountResolution bankAccountResult = decryptAccount(employee.getBankAccount());
            if (bankAccountResult.decryptFailed()) {
                return RouteSnapshot.failed("收款账号解密失败，请重新维护收款信息");
            }
            hasConfiguredAccount = hasConfiguredAccount || bankAccountResult.configured();
            if (StringUtils.hasText(bankAccountResult.value())) {
                settlementAccount = bankAccountResult.value();
                if (!StringUtils.hasText(settlementType)) {
                    settlementType = SettlementAccountType.BANK_CARD.getCode();
                }
            }
        }
        if (!StringUtils.hasText(settlementType) && StringUtils.hasText(settlementAccount)) {
            settlementType = inferSettlementType(settlementAccount);
        }
        if (!StringUtils.hasText(settlementAccount) && !hasConfiguredAccount) {
            String fallback = resolveFallbackAlipayAccount(employee);
            if (StringUtils.hasText(fallback)) {
                settlementAccount = fallback;
                settlementType = SettlementAccountType.ALIPAY.getCode();
            }
        }
        if (!StringUtils.hasText(settlementAccount)) {
            return RouteSnapshot.failed("缺少收款账号（结算账户/银行卡/手机号/邮箱）");
        }

        String accountType = StringUtils.hasText(settlementType) ? settlementType : SettlementAccountType.BANK_CARD.getCode();
        String paymentMethod = resolvePaymentMethod(accountType);
        String providerCode;

        if (EmploymentType.PART_TIME.getCode().equals(employmentType)) {
            if (!SettlementAccountType.ALIPAY.getCode().equals(accountType)) {
                return RouteSnapshot.failed("灵活用工仅支持支付宝收款账户");
            }
            providerCode = "yunzhanghu";
        } else if (EmploymentType.FULL_TIME.getCode().equals(employmentType)) {
            if (!SettlementAccountType.ALIPAY.getCode().equals(accountType)
                    && !SettlementAccountType.BANK_CARD.getCode().equals(accountType)) {
                return RouteSnapshot.failed("全职用工仅支持支付宝或银行卡收款账户");
            }
            providerCode = SettlementRouteProviderResolver.resolveFullTimeProvider(accountType, employee, batch);
        } else {
            return RouteSnapshot.failed("不支持的用工类型: " + employmentType);
        }

        String recipientName = StringUtils.hasText(employee.getSettlementAccountName())
                ? employee.getSettlementAccountName().trim()
                : employee.getName();
        String masked = maskRecipientAccount(settlementAccount, paymentMethod);
        String encrypted = encryptAccount(settlementAccount);

        return RouteSnapshot.supported(
                employee.getName(),
                recipientName,
                encrypted,
                masked,
                accountType,
                paymentMethod,
                providerCode
        );
    }

    private String resolveEmploymentType(PayrollBatch batch, PayrollLine line, Employee employee) {
        if (line != null && StringUtils.hasText(line.getEmploymentType())) {
            return line.getEmploymentType();
        }
        if (employee != null && StringUtils.hasText(employee.getEmploymentType())) {
            return employee.getEmploymentType();
        }
        return batch != null ? batch.getType() : null;
    }

    private String normalizeEmploymentType(String employmentType) {
        if (!StringUtils.hasText(employmentType)) {
            return null;
        }
        String normalized = employmentType.trim().toLowerCase();
        return switch (normalized) {
            case "fulltime", "full-time", "full_time" -> EmploymentType.FULL_TIME.getCode();
            case "parttime", "part-time", "part_time" -> EmploymentType.PART_TIME.getCode();
            default -> normalized;
        };
    }

    private String normalizeSettlementType(String settlementType) {
        if (!StringUtils.hasText(settlementType)) {
            return null;
        }
        String normalized = settlementType.trim().toLowerCase();
        return switch (normalized) {
            case "bank", "bankcard", "bank_card" -> SettlementAccountType.BANK_CARD.getCode();
            case "alipay" -> SettlementAccountType.ALIPAY.getCode();
            case "wechat", "weixin", "wx" -> SettlementAccountType.WECHAT.getCode();
            case "other" -> SettlementAccountType.OTHER.getCode();
            default -> normalized;
        };
    }

    private String inferSettlementType(String account) {
        if (!StringUtils.hasText(account)) {
            return SettlementAccountType.BANK_CARD.getCode();
        }
        if (ValidationUtils.isValidPhone(account) || ValidationUtils.isValidEmail(account)) {
            return SettlementAccountType.ALIPAY.getCode();
        }
        return SettlementAccountType.BANK_CARD.getCode();
    }

    private String resolveFallbackAlipayAccount(Employee employee) {
        if (employee == null) {
            return null;
        }
        if (StringUtils.hasText(employee.getPhone())) {
            return employee.getPhone().trim();
        }
        if (StringUtils.hasText(employee.getEmail())) {
            return employee.getEmail().trim();
        }
        return null;
    }

    private String resolvePaymentMethod(String accountType) {
        return switch (accountType) {
            case "alipay" -> "ALIPAY";
            case "bank_card" -> "BANK_CARD";
            case "wechat" -> "WECHAT";
            case "other" -> "OTHER";
            default -> "UNKNOWN";
        };
    }

    private AccountResolution decryptAccount(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue)) {
            return AccountResolution.missing();
        }
        try {
            String plainAccount = encryptionService.decrypt(encryptedValue);
            if (StringUtils.hasText(plainAccount)) {
                return AccountResolution.resolved(plainAccount.trim());
            }
            log.warn("解密收款账户结果为空，阻断发放路由");
            return AccountResolution.failed();
        } catch (Exception ex) {
            if (SettlementAccountPlaintextGuard.isRecognizedPlainAccount(encryptedValue)) {
                log.warn("解密收款账户失败，按历史明文账号兼容处理: {}", ex.getMessage());
                return AccountResolution.resolved(encryptedValue.trim());
            }
            log.warn("解密收款账户失败，且原始值不像合法明文账号，阻断发放路由: {}", ex.getMessage());
            return AccountResolution.failed();
        }
    }

    private String encryptAccount(String plainAccount) {
        if (!StringUtils.hasText(plainAccount)) {
            return null;
        }
        return encryptionService.encrypt(plainAccount.trim());
    }

    private String maskRecipientAccount(String account, String paymentMethod) {
        if (!StringUtils.hasText(account)) {
            return "-";
        }
        String normalizedMethod = StringUtils.hasText(paymentMethod) ? paymentMethod.trim().toUpperCase() : "UNKNOWN";
        String normalizedAccount = account.trim();
        if ("BANK_CARD".equals(normalizedMethod)) {
            return encryptionService.maskBankAccount(normalizedAccount.replaceAll("\\s+", ""));
        }
        if (normalizedAccount.contains("@")) {
            int atIndex = normalizedAccount.indexOf('@');
            if (atIndex <= 1) {
                return "****";
            }
            String localPart = normalizedAccount.substring(0, atIndex);
            String domainPart = normalizedAccount.substring(atIndex);
            if (localPart.length() <= 2) {
                return localPart.substring(0, 1) + "***" + domainPart;
            }
            return localPart.substring(0, 2) + "***" + domainPart;
        }
        if (ValidationUtils.isValidPhone(normalizedAccount)) {
            return encryptionService.maskPhone(normalizedAccount);
        }
        if (normalizedAccount.length() <= 4) {
            return "****";
        }
        return normalizedAccount.substring(0, 2) + "****" + normalizedAccount.substring(normalizedAccount.length() - 2);
    }

    public record RouteSnapshot(
            boolean supported,
            String employeeName,
            String recipientName,
            String accountNoEncrypted,
            String accountNoMasked,
            String accountType,
            String paymentMethod,
            String providerCode,
            String failureReason
    ) {
        public static RouteSnapshot supported(String employeeName,
                                              String recipientName,
                                              String accountNoEncrypted,
                                              String accountNoMasked,
                                              String accountType,
                                              String paymentMethod,
                                              String providerCode) {
            return new RouteSnapshot(true, employeeName, recipientName, accountNoEncrypted, accountNoMasked,
                    accountType, paymentMethod, providerCode, null);
        }

        public static RouteSnapshot failed(String failureReason) {
            return new RouteSnapshot(false, null, null, null, null, null, null, null, failureReason);
        }
    }

    private record AccountResolution(String value, boolean configured, boolean decryptFailed) {
        private static AccountResolution missing() {
            return new AccountResolution(null, false, false);
        }

        private static AccountResolution resolved(String value) {
            return new AccountResolution(value, true, false);
        }

        private static AccountResolution failed() {
            return new AccountResolution(null, true, true);
        }
    }
}
