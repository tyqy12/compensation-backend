package com.yiyundao.compensation.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限注解常量引用
 * <p>
 * 提供类型安全的权限注解，避免硬编码字符串
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
    @PreAuthorize("isAuthenticated()")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsAuthenticated {
    }

    /**
     * 管理员专有
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsAdmin {
    }

    /**
     * 经理角色
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsManagerOrAdmin {
    }

    /**
     * 财务角色
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsFinanceOrAdmin {
    }

    /**
     * HR角色
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsHrOrAdmin {
    }

    /**
     * 财务或HR
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'HR')")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsFinanceOrHrOrAdmin {
    }

    /**
     * 经理或财务
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'MANAGER')")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsFinanceOrManagerOrAdmin {
    }

    /**
     * 财务或HR或经理
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'HR', 'MANAGER')")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsFinanceOrHrOrManagerOrAdmin {
    }

    /**
     * 员工
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'EMPLOYEE')")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsEmployeeOrFinanceOrAdmin {
    }

    // ==================== 权限注解 ====================

    /**
     * 组织同步权限
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('org:sync')")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HasOrgSyncPermission {
    }

    /**
     * 组织读取权限
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('org:read')")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HasOrgReadPermission {
    }

    // ==================== OpenAPI 权限注解 ====================

    /**
     * 外部应用访问权限
     */
    @PreAuthorize("hasAuthority('ROLE_APP')")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsApp {
    }

    /**
     * OpenAPI 工资条读取权限
     */
    @PreAuthorize("hasAuthority('SCOPE_payslip:read')")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HasPayslipReadScope {
    }

    /**
     * OpenAPI 薪酬读取权限
     */
    @PreAuthorize("hasAuthority('SCOPE_payroll:read')")
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HasPayrollReadScope {
    }

    // ==================== 表达式辅助方法 ====================

    /**
     * 生成角色表达式
     *
     * @param roles 角色数组
     * @return SpEL 表达式
     */
    public static String hasAnyRole(String... roles) {
        StringBuilder sb = new StringBuilder("hasAnyRole(");
        for (int i = 0; i < roles.length; i++) {
            if (i > 0) sb.append(", ");
            String role = roles[i];
            // 确保角色名不带 ROLE_ 前缀（Spring Security 要求）
            if (role.startsWith("ROLE_")) {
                role = role.substring(5);
            }
            sb.append("'").append(role).append("'");
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * 生成权限表达式
     *
     * @param authorities 权限数组
     * @return SpEL 表达式
     */
    public static String hasAnyAuthority(String... authorities) {
        StringBuilder sb = new StringBuilder("hasAnyAuthority(");
        for (int i = 0; i < authorities.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("'").append(authorities[i]).append("'");
        }
        sb.append(")");
        return sb.toString();
    }
}
