package com.yiyundao.compensation.modules.payment.support;

import com.yiyundao.compensation.common.utils.SettlementAccountPlaintextGuard;
import com.yiyundao.compensation.common.utils.ValidationUtils;
import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.enums.SettlementAccountType;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payment.service.SettlementProviderRoutingService;
import com.yiyundao.compensation.service.EncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 薪资收款路由规则。支付渠道实现只接收已经解析好的结果，不参与业务路由判断。
 */
@Slf4j
public final class SettlementRecipientRouteResolver {

    private SettlementRecipientRouteResolver() {
    }

    public static RouteResult resolve(PayrollBatch batch,
                                      PayrollLine line,
                                      Employee employee,
                                      EncryptionService encryptionService) {
        return resolve(batch, line, employee, encryptionService, null);
    }

    public static RouteResult resolve(PayrollBatch batch,
                                      PayrollLine line,
                                      Employee employee,
                                      EncryptionService encryptionService,
                                      SettlementProviderRoutingService routingService) {
        if (employee == null) {
            return RouteResult.failed("ACCOUNT_MISSING", "员工信息不存在");
        }

        String employmentType = normalizeEmploymentType(resolveEmploymentType(batch, line, employee));
        if (!StringUtils.hasText(employmentType)) {
            return RouteResult.failed("EMPLOYMENT_TYPE_MISSING", "缺少用工类型，无法路由结算渠道");
        }

        String settlementType = normalizeSettlementType(employee.getSettlementAccountType());
        AccountResolution settlementAccountResult = decryptAccount(employee.getSettlementAccount(), encryptionService);
        if (settlementAccountResult.decryptFailed()) {
            return RouteResult.failed("ACCOUNT_DECRYPT_FAILED", "收款账号解密失败，请重新维护收款信息");
        }
        String settlementAccount = settlementAccountResult.value();
        boolean hasConfiguredAccount = settlementAccountResult.configured();

        if (!StringUtils.hasText(settlementAccount)) {
            AccountResolution bankAccountResult = decryptAccount(employee.getBankAccount(), encryptionService);
            if (bankAccountResult.decryptFailed()) {
                return RouteResult.failed("ACCOUNT_DECRYPT_FAILED", "收款账号解密失败，请重新维护收款信息");
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
            String fallbackAlipayAccount = resolveFallbackAlipayAccount(employee);
            if (StringUtils.hasText(fallbackAlipayAccount)) {
                settlementAccount = fallbackAlipayAccount;
                settlementType = SettlementAccountType.ALIPAY.getCode();
            }
        }

        if (!StringUtils.hasText(settlementAccount)) {
            return RouteResult.failed("ACCOUNT_MISSING", "缺少收款账号（结算账户/银行卡/手机号/邮箱）");
        }

        String accountType = StringUtils.hasText(settlementType)
                ? settlementType
                : SettlementAccountType.BANK_CARD.getCode();
        String paymentMethod = resolvePaymentMethod(accountType);

        if (EmploymentType.PART_TIME.getCode().equals(employmentType)) {
            if (!SettlementAccountType.ALIPAY.getCode().equals(accountType)) {
                return RouteResult.failed(accountType, settlementAccount, paymentMethod, "yunzhanghu",
                        "ACCOUNT_TYPE_UNSUPPORTED", "灵活用工仅支持支付宝收款账户");
            }
            return RouteResult.supported(accountType, settlementAccount, "ALIPAY", "yunzhanghu");
        }

        if (EmploymentType.FULL_TIME.getCode().equals(employmentType)) {
            String providerCode = resolveFullTimeProvider(accountType, employee, batch, routingService);
            if (SettlementAccountType.ALIPAY.getCode().equals(accountType)) {
                return RouteResult.supported(accountType, settlementAccount, "ALIPAY", providerCode);
            }
            if (SettlementAccountType.BANK_CARD.getCode().equals(accountType)) {
                return RouteResult.supported(accountType, settlementAccount, "BANK_CARD", providerCode);
            }
            return RouteResult.failed(accountType, settlementAccount, paymentMethod, "alipay",
                    "ACCOUNT_TYPE_UNSUPPORTED", "全职用工仅支持支付宝或银行卡收款账户");
        }

        return RouteResult.failed(accountType, settlementAccount, paymentMethod, "unknown",
                "EMPLOYMENT_TYPE_UNSUPPORTED", "不支持的用工类型: " + employmentType);
    }

    private static String resolveFullTimeProvider(String accountType,
                                                  Employee employee,
                                                  PayrollBatch batch,
                                                  SettlementProviderRoutingService routingService) {
        if (SettlementAccountType.BANK_CARD.getCode().equals(accountType)) {
            return SettlementRouteProviderResolver.resolveFullTimeProvider(accountType, "alipay");
        }
        if (routingService != null) {
            try {
                String routedProvider = routingService.determineProvider(employee, batch);
                return SettlementRouteProviderResolver.resolveFullTimeProvider(accountType, routedProvider);
            } catch (Exception ex) {
                log.warn("结算渠道路由失败，使用员工/批次配置兜底: employeeId={}, msg={}",
                        employee != null ? employee.getId() : null, ex.getMessage());
            }
        }
        return SettlementRouteProviderResolver.resolveFullTimeProvider(accountType, employee, batch);
    }

    private static String resolveEmploymentType(PayrollBatch batch, PayrollLine line, Employee employee) {
        String employmentType = line != null ? line.getEmploymentType() : null;
        if (!StringUtils.hasText(employmentType) && employee != null) {
            employmentType = employee.getEmploymentType();
        }
        if (!StringUtils.hasText(employmentType) && batch != null) {
            employmentType = batch.getType();
        }
        return employmentType;
    }

    private static String normalizeEmploymentType(String employmentType) {
        if (!StringUtils.hasText(employmentType)) {
            return null;
        }
        return switch (employmentType.trim().toLowerCase()) {
            case "fulltime", "full-time", "full_time" -> EmploymentType.FULL_TIME.getCode();
            case "parttime", "part-time", "part_time" -> EmploymentType.PART_TIME.getCode();
            default -> employmentType.trim().toLowerCase();
        };
    }

    private static String normalizeSettlementType(String settlementType) {
        if (!StringUtils.hasText(settlementType)) {
            return null;
        }
        return switch (settlementType.trim().toLowerCase()) {
            case "bank", "bankcard", "bank_card" -> SettlementAccountType.BANK_CARD.getCode();
            case "alipay" -> SettlementAccountType.ALIPAY.getCode();
            case "wechat", "weixin", "wx" -> SettlementAccountType.WECHAT.getCode();
            case "other" -> SettlementAccountType.OTHER.getCode();
            default -> settlementType.trim().toLowerCase();
        };
    }

    private static String inferSettlementType(String account) {
        if (!StringUtils.hasText(account)) {
            return SettlementAccountType.BANK_CARD.getCode();
        }
        return ValidationUtils.isValidPhone(account) || ValidationUtils.isValidEmail(account)
                ? SettlementAccountType.ALIPAY.getCode()
                : SettlementAccountType.BANK_CARD.getCode();
    }

    private static String resolveFallbackAlipayAccount(Employee employee) {
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

    private static String resolvePaymentMethod(String accountType) {
        return switch (accountType) {
            case "alipay" -> "ALIPAY";
            case "bank_card" -> "BANK_CARD";
            case "wechat" -> "WECHAT";
            case "other" -> "OTHER";
            default -> "UNKNOWN";
        };
    }

    private static AccountResolution decryptAccount(String encryptedValue, EncryptionService encryptionService) {
        if (!StringUtils.hasText(encryptedValue)) {
            return AccountResolution.missing();
        }
        try {
            String plainAccount = encryptionService.decrypt(encryptedValue);
            if (StringUtils.hasText(plainAccount)) {
                return AccountResolution.resolved(plainAccount.trim());
            }
            log.warn("解密收款账户结果为空，阻断支付路由");
            return AccountResolution.failed();
        } catch (Exception ex) {
            if (SettlementAccountPlaintextGuard.isRecognizedPlainAccount(encryptedValue)) {
                log.warn("解密收款账户失败，按历史明文账号兼容处理: {}", ex.getMessage());
                return AccountResolution.resolved(encryptedValue.trim());
            }
            log.warn("解密收款账户失败，且原始值不像合法明文账号，阻断支付路由: {}", ex.getMessage());
            return AccountResolution.failed();
        }
    }

    public record RouteResult(
            boolean supported,
            String accountType,
            String recipientAccount,
            String paymentMethod,
            String providerCode,
            String errorCode,
            String errorMsg
    ) {
        public static RouteResult supported(String accountType,
                                             String recipientAccount,
                                             String paymentMethod,
                                             String providerCode) {
            return new RouteResult(true, accountType, recipientAccount, paymentMethod, providerCode, null, null);
        }

        public static RouteResult failed(String errorCode, String errorMsg) {
            return new RouteResult(false, null, null, "UNKNOWN", "unknown", errorCode, errorMsg);
        }

        public static RouteResult failed(String accountType,
                                         String recipientAccount,
                                         String paymentMethod,
                                         String providerCode,
                                         String errorCode,
                                         String errorMsg) {
            return new RouteResult(false, accountType, recipientAccount, paymentMethod, providerCode, errorCode, errorMsg);
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
