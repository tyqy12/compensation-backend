package com.yiyundao.compensation.common.encrypt;

/**
 * 敏感数据类型枚举
 * <p>
 * 定义了常用的脱敏规则：
 * </p>
 * <ul>
 *   <li>ID_CARD - 身份证号：保留前3后4位，中间用*替代</li>
 *   <li>PHONE - 手机号：保留前3后4位，中间用*替代</li>
 *   <li>BANK_CARD - 银行卡号：保留前6后4位，中间用*替代</li>
 *   <li>NAME - 姓名：只保留第一个字</li>
 *   <li>EMAIL - 邮箱：前2字符@域名</li>
 *   <li>ADDRESS - 地址：保留前6字符</li>
 *   <li>PASSWORD - 密码：全部显示为*</li>
 *   <li>CUSTOM - 自定义：使用注解指定的 prefix/suffix</li>
 *   <li>DEFAULT - 默认：全部显示为*</li>
 *   <li>NONE - 不脱敏</li>
 * </ul>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
public enum SensitiveType {

    /**
     * 身份证号：110***********1234
     */
    ID_CARD {
        @Override
        public String desensitize(String value) {
            if (value == null || value.length() < 8) {
                return value;
            }
            return value.substring(0, 3) + "*".repeat(Math.max(0, value.length() - 7)) + value.substring(value.length() - 4);
        }
    },

    /**
     * 手机号：138****1234
     */
    PHONE {
        @Override
        public String desensitize(String value) {
            if (value == null || value.length() < 7) {
                return value;
            }
            return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
        }
    },

    /**
     * 银行卡号：6222****1234
     */
    BANK_CARD {
        @Override
        public String desensitize(String value) {
            if (value == null || value.length() < 10) {
                return value;
            }
            return value.substring(0, 6) + "*".repeat(Math.max(0, value.length() - 10)) + value.substring(value.length() - 4);
        }
    },

    /**
     * 姓名：张*
     */
    NAME {
        @Override
        public String desensitize(String value) {
            if (value == null || value.isEmpty()) {
                return value;
            }
            if (value.length() == 1) {
                return value;
            }
            return value.substring(0, 1) + "*";
        }
    },

    /**
     * 姓名2（保留最后一个字）：*三
     */
    NAME_LAST {
        @Override
        public String desensitize(String value) {
            if (value == null || value.isEmpty()) {
                return value;
            }
            if (value.length() == 1) {
                return value;
            }
            return "*" + value.substring(value.length() - 1);
        }
    },

    /**
     * 邮箱：x**@example.com
     */
    EMAIL {
        @Override
        public String desensitize(String value) {
            if (value == null || !value.contains("@")) {
                return value;
            }
            int atIndex = value.indexOf("@");
            if (atIndex <= 2) {
                return value;
            }
            String prefix = value.substring(0, 2);
            return prefix + "**" + value.substring(atIndex);
        }
    },

    /**
     * 地址：北京市海淀区****
     */
    ADDRESS {
        @Override
        public String desensitize(String value) {
            if (value == null || value.length() < 6) {
                return value;
            }
            return value.substring(0, 6) + "*";
        }
    },

    /**
     * 密码：********
     */
    PASSWORD {
        @Override
        public String desensitize(String value) {
            if (value == null) {
                return null;
            }
            return "*".repeat(Math.min(value.length(), 20));
        }
    },

    /**
     * 金额：1234.56 -> 1***.56
     */
    AMOUNT {
        @Override
        public String desensitize(String value) {
            if (value == null || value.length() < 5) {
                return value;
            }
            return value.substring(0, 1) + "***" + value.substring(value.length() - 3);
        }
    },

    /**
     * 自定义脱敏
     */
    CUSTOM {
        @Override
        public String desensitize(String value) {
            return value; // 由具体注解参数决定
        }
    },

    /**
     * 默认脱敏：********
     */
    DEFAULT {
        @Override
        public String desensitize(String value) {
            if (value == null) {
                return null;
            }
            return "*".repeat(Math.min(value.length(), 20));
        }
    },

    /**
     * 不脱敏
     */
    NONE {
        @Override
        public String desensitize(String value) {
            return value;
        }
    };

    /**
     * 脱敏处理
     *
     * @param value 原始值
     * @return 脱敏后的值
     */
    public abstract String desensitize(String value);

    /**
     * 脱敏处理（带参数）
     *
     * @param value   原始值
     * @param prefix  前缀
     * @param suffix  后缀
     * @return 脱敏后的值
     */
    public String desensitize(String value, String prefix, String suffix) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (this == CUSTOM) {
            int keepLength = prefix.length() + suffix.length();
            if (value.length() <= keepLength) {
                return prefix + suffix;
            }
            return prefix + "*".repeat(value.length() - keepLength) + suffix;
        }
        return desensitize(value);
    }
}
