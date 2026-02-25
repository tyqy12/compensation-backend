package com.yiyundao.compensation.common.annotation;

import java.lang.annotation.*;

/**
 * API 版本注解
 * <p>
 * 用于标记 Controller 或方法的 API 版本号。
 * 版本号从 1 开始，支持多版本共存。
 * </p>
 *
 * 使用方式：
 * <pre>
 * // 类级别注解：该类下所有接口使用 v1
 * {@code @}ApiVersion(1)
 * {@code @}RestController
 * public class EmployeeController {}
 *
 * // 方法级别注解：覆盖类级别注解
 * {@code @}ApiVersion(2)
 * {@code @}GetMapping("/employee")
 * public ApiResponse<EmployeeVO> getEmployee() {}
 * </pre>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

    /**
     * API 版本号
     * <p>
     * 支持的版本号：1, 2, 3...
     * 默认版本为 1
     * </p>
     *
     * @return 版本号
     */
    int value() default 1;

    /**
     * 版本描述
     *
     * @return 版本描述信息
     */
    String description() default "";
}
