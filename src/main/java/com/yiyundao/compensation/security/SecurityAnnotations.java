package com.yiyundao.compensation.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限注解常量引用
 * <p>
 * 提供兼容旧代码的注解名称。所有注解都委托同一个数据库权限决策器，
 * 注解名称本身不再代表任何角色或权限策略。
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
public final class SecurityAnnotations {

    private SecurityAnnotations() {
        // 防止实例化
    }

    // ==================== 角色权限注解 ====================

    /**
     * 已认证用户
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsAuthenticated {
    }

    /**
     * 管理员专有
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsAdmin {
    }

    /**
     * 经理角色
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsManagerOrAdmin {
    }

    /**
     * 财务角色
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsFinanceOrAdmin {
    }

    /**
     * HR角色
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsHrOrAdmin {
    }

    /**
     * 财务或HR
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsFinanceOrHrOrAdmin {
    }

    /**
     * 经理或财务
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsFinanceOrManagerOrAdmin {
    }

    /**
     * 财务或HR或经理
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsFinanceOrHrOrManagerOrAdmin {
    }

    /**
     * 员工
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsEmployeeOrFinanceOrAdmin {
    }

    // ==================== 权限注解 ====================

    /**
     * 组织同步权限
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HasOrgSyncPermission {
    }

    /**
     * 组织读取权限
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HasOrgReadPermission {
    }

    /**
     * 组织同步管理读取权限
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HasOrgAdminReadPermission {
    }

    // ==================== OpenAPI 权限注解 ====================

    /**
     * 外部应用访问权限
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsApp {
    }

    /**
     * OpenAPI 工资条读取权限
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HasPayslipReadScope {
    }

    /**
     * OpenAPI 薪酬读取权限
     */
    @PreAuthorize("@databaseMethodAuthorizationEvaluator.check(authentication)")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HasPayrollReadScope {
    }

}
