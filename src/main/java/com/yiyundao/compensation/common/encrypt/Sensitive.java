package com.yiyundao.compensation.common.encrypt;

import java.lang.annotation.*;

/**
 * 敏感数据脱敏注解
 * <p>
 * 用于标记需要脱敏的字段，支持多种脱敏规则。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Sensitive {

    /**
     * 脱敏类型
     *
     * @return 脱敏类型
     */
    SensitiveType value() default SensitiveType.DEFAULT;

    /**
     * 自定义前缀（用于 CUSTOM 类型）
     *
     * @return 前缀
     */
    String prefix() default "";

    /**
     * 自定义后缀（用于 CUSTOM 类型）
     *
     * @return 后缀
     */
    String suffix() default "";

    /**
     * 保留长度（用于自定义保留长度）
     *
     * @return 保留长度
     */
    int keepLength() default 0;
}
