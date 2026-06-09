package com.yiyundao.compensation.common.utils;

import org.springframework.util.StringUtils;

/**
 * 判断解密失败后的收款账号字段是否仍可按历史明文兼容处理。
 */
public final class SettlementAccountPlaintextGuard {

    private SettlementAccountPlaintextGuard() {
    }

    public static boolean isRecognizedPlainAccount(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim();
        String compact = normalized.replaceAll("\\s+", "");
        return ValidationUtils.isValidPhone(compact)
                || ValidationUtils.isValidEmail(normalized)
                || ValidationUtils.isValidBankCard(compact);
    }
}
