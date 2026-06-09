package com.yiyundao.compensation.security;

/**
 * 安全相关的常量定义
 * <p>
 * 集中管理所有角色和权限常量，消除硬编码问题
 * </p>
 *
 * @author 芙宁娜
 * @since 2025-01-10
 */
public final class SecurityConstants {

    private SecurityConstants() {
        // 防止实例化
    }

    // ==================== 角色定义 ====================

    /**
     * 系统管理员角色
     */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    /**
     * 部门经理角色
     */
    public static final String ROLE_MANAGER = "ROLE_MANAGER";

    /**
     * 财务人员角色
     */
    public static final String ROLE_FINANCE = "ROLE_FINANCE";

    /**
     * 人力资源角色
     */
    public static final String ROLE_HR = "ROLE_HR";

    /**
     * 普通员工角色
     */
    public static final String ROLE_EMPLOYEE = "ROLE_EMPLOYEE";

    /**
     * 基础用户角色（所有已认证用户默认拥有）
     */
    public static final String ROLE_USER = "ROLE_USER";

    /**
     * 外部应用角色（OpenAPI访问）
     */
    public static final String ROLE_APP = "ROLE_APP";

    // ==================== 权限定义 ====================

    /**
     * 组织同步权限
     */
    public static final String AUTH_ORG_SYNC = "org:sync";

    /**
     * 组织读取权限
     */
    public static final String AUTH_ORG_READ = "org:read";

    /**
     * 员工解密权限（身份证/银行卡等敏感数据）
     */
    public static final String AUTH_EMPLOYEE_DECRYPT = "employee:decrypt";

    /**
     * 支付执行权限
     */
    public static final String AUTH_PAYMENT_EXECUTE = "payment:execute";

    /**
     * 报表查看权限
     */
    public static final String AUTH_REPORT_VIEW = "report:view";

    // ==================== OAuth Scope 定义 ====================

    /**
     * OpenAPI: 工资条读取权限
     */
    public static final String SCOPE_PAYSLIP_READ = "SCOPE_payslip:read";

    /**
     * OpenAPI: 薪酬读取权限
     */
    public static final String SCOPE_PAYROLL_READ = "SCOPE_payroll:read";

    // ==================== 缓存配置 ====================

    /**
     * API资源缓存时间（毫秒）- 30秒
     */
    public static final long API_RESOURCE_CACHE_TTL_MS = 30_000L;

    /**
     * 权限配置缓存时间（毫秒）- 5分钟
     */
    public static final long PERMISSION_CONFIG_CACHE_TTL_MS = 300_000L;

    // ==================== 路径模式定义 ====================

    /**
     * 管理后台路径模式
     */
    public static final String PATTERN_ADMIN = "/admin/**";

    /**
     * 系统集成路径模式
     */
    public static final String PATTERN_SYSTEM_INTEGRATION = "/system/integration/**";

    /**
     * 经理专用路径模式
     */
    public static final String PATTERN_MANAGER = "/manager/**";

    // ==================== 审批流程配置键 ====================

    /**
     * 薪资审批流程配置键
     */
    public static final String CONFIG_PAYROLL_APPROVAL_FLOW = "payroll.approval.flow";

    /**
     * 临时支付审批流程配置键
     */
    public static final String CONFIG_ADHOC_APPROVAL_FLOW = "adhoc.approval.flow";

    /**
     * 架构外员工审批流程配置键
     */
    public static final String CONFIG_OFFLINE_APPROVAL_FLOW = "offline.approval.flow";

    /**
     * 权限授权审批流程配置键
     */
    public static final String CONFIG_PERMISSION_APPROVAL_FLOW = "permission.approval.flow";

    /**
     * 薪酬异议审批流程配置键
     */
    public static final String CONFIG_PAYROLL_DISPUTE_APPROVAL_FLOW = "payroll.dispute.approval.flow";

    // ==================== 审批角色映射 ====================

    /**
     * 默认审批超时时间（小时）
     */
    public static final int DEFAULT_APPROVAL_TIMEOUT_HOURS = 24;

    /**
     * 最终审批超时时间（小时）
     */
    public static final int FINAL_APPROVAL_TIMEOUT_HOURS = 48;

    // ==================== 公共路径定义 ====================

    /**
     * API文档路径模式
     */
    public static final String[] PATTERNS_OPENAPI_DOCS = {
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/webjars/**"
    };

    /**
     * 健康检查路径模式
     */
    public static final String[] PATTERNS_HEALTH = {
            "/actuator/health",
            "/system/health",
            "/favicon.ico"
    };

    /**
     * 认证相关公共路径模式
     */
    public static final String[] PATTERNS_AUTH_PUBLIC = {
            "/auth/login",
            "/auth/refresh",
            "/auth/oauth/**"
    };

    /**
     * 开发环境专用公共路径模式
     */
    public static final String[] PATTERNS_AUTH_DEV_ONLY = {
            "/auth/dev-token"
    };

    /**
     * 支付通知公共路径模式
     */
    public static final String[] PATTERNS_PAYMENT_NOTIFY = {
            "/alipay/notify",
            "/v1/settlement/callback/**"
    };

    /**
     * 外部应用 OAuth 公共入口。
     *
     * <p>token 端点需要公开到安全链外层，但控制器内部仍会校验 Basic client credentials。</p>
     */
    public static final String[] PATTERNS_EXTERNAL_OAUTH_PUBLIC = {
            "/v1/oauth/token"
    };

    // ==================== 辅助方法 ====================

    /**
     * 获取所有角色常量
     *
     * @return 角色数组
     */
    public static String[] getAllRoles() {
        return new String[]{
                ROLE_ADMIN,
                ROLE_MANAGER,
                ROLE_FINANCE,
                ROLE_HR,
                ROLE_EMPLOYEE,
                ROLE_USER,
                ROLE_APP
        };
    }

    /**
     * 检查是否为系统角色
     *
     * @param role 角色名
     * @return 是否为系统角色
     */
    public static boolean isSystemRole(String role) {
        if (role == null) {
            return false;
        }
        return ROLE_ADMIN.equals(role)
                || ROLE_MANAGER.equals(role)
                || ROLE_FINANCE.equals(role)
                || ROLE_HR.equals(role)
                || ROLE_EMPLOYEE.equals(role)
                || ROLE_USER.equals(role)
                || ROLE_APP.equals(role);
    }

    /**
     * 将角色名转换为 GrantedAuthority 格式（自动添加 ROLE_ 前缀）
     *
     * @param role 原始角色名
     * @return 标准角色名
     */
    public static String toGrantedAuthority(String role) {
        if (role == null) {
            return ROLE_USER;
        }
        if (role.startsWith("ROLE_") || role.startsWith("SCOPE_")) {
            return role;
        }
        return "ROLE_" + role;
    }
}
