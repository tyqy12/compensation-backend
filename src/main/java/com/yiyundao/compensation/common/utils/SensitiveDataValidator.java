package com.yiyundao.compensation.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SensitiveDataValidator {
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^[1-9]\\d{5}(18|19|([23]\\d))\\d{2}((0[1-9])|(10|11|12))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("^\\d{15,19}$");
    // 允许单字中文姓名，范围中文与中点，长度1-20
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5·]{1,20}$");
    private static final Pattern ALIPAY_ACCOUNT_PATTERN = Pattern.compile("^(1[3-9]\\d{9}|[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})$");
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_SINGLE_AMOUNT = new BigDecimal("10000.00");
    private static final BigDecimal MAX_DAILY_AMOUNT = new BigDecimal("50000.00");

    public ValidationResult validateIdCard(String idCard) {
        if (idCard == null || idCard.trim().isEmpty()) return ValidationResult.fail("身份证号不能为空");
        String cleanIdCard = idCard.trim().toUpperCase();
        if (!ID_CARD_PATTERN.matcher(cleanIdCard).matches()) return ValidationResult.fail("身份证号格式不正确");
        if (!isValidIdCardChecksum(cleanIdCard)) return ValidationResult.fail("身份证号校验位不正确");
        return ValidationResult.success();
    }

    public ValidationResult validatePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) return ValidationResult.fail("手机号不能为空");
        String cleanPhone = phone.trim().replaceAll("[\\s-]", "");
        if (!PHONE_PATTERN.matcher(cleanPhone).matches()) return ValidationResult.fail("手机号格式不正确");
        return ValidationResult.success();
    }

    public ValidationResult validateBankCard(String bankCard) {
        if (bankCard == null || bankCard.trim().isEmpty()) return ValidationResult.fail("银行卡号不能为空");
        String cleanBankCard = bankCard.trim().replaceAll("[\\s-]", "");
        if (!BANK_CARD_PATTERN.matcher(cleanBankCard).matches()) return ValidationResult.fail("银行卡号格式不正确");
        if (!isValidLuhnChecksum(cleanBankCard)) return ValidationResult.fail("银行卡号校验失败");
        return ValidationResult.success();
    }

    public ValidationResult validateName(String name) {
        if (name == null || name.trim().isEmpty()) return ValidationResult.fail("姓名不能为空");
        String cleanName = name.trim();
        if (!NAME_PATTERN.matcher(cleanName).matches()) return ValidationResult.fail("姓名格式不正确，只能包含中文字符和·分隔符");
        return ValidationResult.success();
    }

    public ValidationResult validateAlipayAccount(String account) {
        if (account == null || account.trim().isEmpty()) return ValidationResult.fail("支付宝账户不能为空");
        String cleanAccount = account.trim();
        if (!ALIPAY_ACCOUNT_PATTERN.matcher(cleanAccount).matches()) return ValidationResult.fail("支付宝账户格式不正确，应为手机号或邮箱");
        return ValidationResult.success();
    }

    public ValidationResult validateAmount(BigDecimal amount) {
        if (amount == null) return ValidationResult.fail("转账金额不能为空");
        if (amount.compareTo(MIN_AMOUNT) < 0) return ValidationResult.fail("转账金额不能小于" + MIN_AMOUNT + "元");
        if (amount.compareTo(MAX_SINGLE_AMOUNT) > 0) return ValidationResult.fail("单笔转账金额不能超过" + MAX_SINGLE_AMOUNT + "元");
        if (amount.scale() > 2) return ValidationResult.fail("转账金额最多保留2位小数");
        return ValidationResult.success();
    }

    public ValidationResult validateDailyAmount(BigDecimal totalAmount) {
        if (totalAmount == null) return ValidationResult.fail("每日转账总额不能为空");
        if (totalAmount.compareTo(MAX_DAILY_AMOUNT) > 0) return ValidationResult.fail("每日转账总额不能超过" + MAX_DAILY_AMOUNT + "元");
        return ValidationResult.success();
    }

    public ValidationResult validateRemark(String remark) {
        if (remark != null) {
            String cleanRemark = remark.trim();
            if (cleanRemark.length() > 100) return ValidationResult.fail("转账备注不能超过100个字符");
            if (containsSensitiveWords(cleanRemark)) return ValidationResult.fail("转账备注包含敏感词汇");
        }
        return ValidationResult.success();
    }

    private boolean isValidIdCardChecksum(String idCard) {
        if (idCard.length() != 18) return false;
        int[] weights = {7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2};
        char[] checksums = {'1','0','X','9','8','7','6','5','4','3','2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            char c = idCard.charAt(i);
            if (!Character.isDigit(c)) return false;
            sum += (c - '0') * weights[i];
        }
        char expectedChecksum = checksums[sum % 11];
        char actualChecksum = idCard.charAt(17);
        return expectedChecksum == actualChecksum;
    }

    private boolean isValidLuhnChecksum(String cardNumber) {
        int sum = 0; boolean alternate = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            char c = cardNumber.charAt(i);
            if (!Character.isDigit(c)) return false;
            int digit = c - '0';
            if (alternate) { digit *= 2; if (digit > 9) digit = (digit % 10) + 1; }
            sum += digit; alternate = !alternate;
        }
        return (sum % 10) == 0;
    }

    private boolean containsSensitiveWords(String text) {
        String[] sensitiveWords = {"赌博","彩票","贷款","洗钱","诈骗","传销","毒品","枪支","爆炸","恐怖","政治","色情"};
        String lowerText = text.toLowerCase();
        for (String word : sensitiveWords) {
            if (lowerText.contains(word)) {
                log.warn("检测到敏感词汇: {}", word);
                return true;
            }
        }
        return false;
    }

    public static class ValidationResult {
        private final boolean valid; private final String message;
        private ValidationResult(boolean valid, String message) { this.valid = valid; this.message = message; }
        public static ValidationResult success() { return new ValidationResult(true, null); }
        public static ValidationResult fail(String message) { return new ValidationResult(false, message); }
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        @Override public String toString() { return valid ? "Valid" : "Invalid: " + message; }
    }
}
