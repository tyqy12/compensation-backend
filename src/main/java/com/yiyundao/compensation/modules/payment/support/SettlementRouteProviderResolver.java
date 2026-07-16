package com.yiyundao.compensation.modules.payment.support;

import com.yiyundao.compensation.enums.SettlementAccountType;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import org.springframework.util.StringUtils;

public final class SettlementRouteProviderResolver {

    private static final String DEFAULT_PROVIDER = "alipay";
    private static final String YUNZHANGHU_PROVIDER = "yunzhanghu";

    private SettlementRouteProviderResolver() {
    }

    public static String resolveFullTimeProvider(String accountType, Employee employee, PayrollBatch batch) {
        return resolveFullTimeProvider(accountType, resolveConfiguredProvider(employee, batch, DEFAULT_PROVIDER));
    }

    public static String resolveFullTimeProvider(String accountType, String configuredProvider) {
        if (SettlementAccountType.BANK_CARD.getCode().equals(accountType)) {
            return DEFAULT_PROVIDER;
        }
        String provider = normalizeProviderCode(configuredProvider, DEFAULT_PROVIDER);
        return YUNZHANGHU_PROVIDER.equals(provider) ? DEFAULT_PROVIDER : provider;
    }

    public static String resolveConfiguredProvider(Employee employee, PayrollBatch batch, String fallbackProvider) {
        if (employee != null && StringUtils.hasText(employee.getSettlementProviderCode())) {
            return normalizeProviderCode(employee.getSettlementProviderCode(), fallbackProvider);
        }
        if (batch != null && StringUtils.hasText(batch.getSettlementProviderCode())) {
            return normalizeProviderCode(batch.getSettlementProviderCode(), fallbackProvider);
        }
        return normalizeProviderCode(fallbackProvider, DEFAULT_PROVIDER);
    }

    public static String normalizeProviderCode(String providerCode, String fallbackProvider) {
        if (StringUtils.hasText(providerCode)) {
            return providerCode.trim().toLowerCase();
        }
        return StringUtils.hasText(fallbackProvider) ? fallbackProvider.trim().toLowerCase() : DEFAULT_PROVIDER;
    }
}
