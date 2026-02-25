package com.yiyundao.compensation.common.annotation;

import lombok.Getter;

@Getter
public enum SensitiveType {

    ID_CARD("身份证号", "(\\d{3})\\d{11}(\\d{4})", "$1***********$2"),

    PHONE("手机号", "(\\d{3})\\d{4}(\\d{4})", "$1****$2"),

    BANK_CARD("银行卡号", "(\\d{4})\\d+(\\d{4})", "$1**********$2"),

    NAME("姓名", "(.{1})(.{0,})(.{1})", "$1*"),

    EMAIL("邮箱", "(.{2})[^@]+(@.+)", "$1**$2"),

    ADDRESS("地址", "(.{6}).+(.{4})", "$1********$2"),

    DEFAULT("默认", "(.+)", "***");

    private final String description;
    private final String regex;
    private final String replacement;

    SensitiveType(String description, String regex, String replacement) {
        this.description = description;
        this.regex = regex;
        this.replacement = replacement;
    }

    public String desensitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (this == ID_CARD) {
            return maskKeep(value, 3, 4);
        }
        if (this == NAME && value.length() == 1) {
            return "*";
        }
        return value.replaceAll(regex, replacement);
    }

    private static String maskKeep(String value, int keepPrefix, int keepSuffix) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            return "";
        }
        if (keepPrefix < 0 || keepSuffix < 0) {
            return value;
        }
        if (value.length() <= keepPrefix + keepSuffix) {
            return value;
        }
        String prefix = value.substring(0, keepPrefix);
        String suffix = value.substring(value.length() - keepSuffix);
        int maskLength = value.length() - keepPrefix - keepSuffix;
        return prefix + "*".repeat(maskLength) + suffix;
    }
}
