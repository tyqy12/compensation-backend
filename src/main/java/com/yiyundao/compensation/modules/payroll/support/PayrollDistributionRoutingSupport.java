package com.yiyundao.compensation.modules.payroll.support;

import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payment.support.SettlementRecipientRouteResolver;
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
        SettlementRecipientRouteResolver.RouteResult route = SettlementRecipientRouteResolver.resolve(
                batch, line, employee, encryptionService);
        if (!route.supported()) {
            return RouteSnapshot.failed(route.errorMsg());
        }

        String recipientName = StringUtils.hasText(employee.getSettlementAccountName())
                ? employee.getSettlementAccountName().trim()
                : employee.getName();
        String encrypted = encryptionService.encrypt(route.recipientAccount().trim());
        String masked = maskRecipientAccount(route.recipientAccount(), route.paymentMethod());
        return RouteSnapshot.supported(
                employee.getName(),
                recipientName,
                encrypted,
                masked,
                route.accountType(),
                route.paymentMethod(),
                route.providerCode()
        );
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
        if (com.yiyundao.compensation.common.utils.ValidationUtils.isValidPhone(normalizedAccount)) {
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
}
