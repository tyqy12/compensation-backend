package com.yiyundao.compensation.common.annotation;

import java.lang.annotation.*;

/**
 * 忽略 API 版本控制注解
 * <p>
 * 标记某些接口不参与版本控制，通常用于：
 * - 公共接口（健康检查、认证接口）
 * - 外部 API（OpenAPI）
 * - 内部管理接口
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-10
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreVersion {
}
