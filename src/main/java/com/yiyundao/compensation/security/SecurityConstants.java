package com.yiyundao.compensation.security;

/**
 * 非授权业务配置常量。
 *
 * <p>访问资源、操作和主体授权不在代码中声明，统一由数据库权限目录驱动。</p>
 */
public final class SecurityConstants {

    private SecurityConstants() {
        // 防止实例化
    }

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
    public static final String CONFIG_EMPLOYEE_PROFILE_CHANGE_APPROVAL_FLOW = "employee.profile-change.approval.flow";
    public static final String CONFIG_PLATFORM_BIND_APPROVAL_FLOW = "platform.bind.approval.flow";

    /**
     * 权限授权审批流程配置键
     */
    public static final String CONFIG_PERMISSION_APPROVAL_FLOW = "permission.approval.flow";

    /**
     * 薪酬异议审批流程配置键
     */
    public static final String CONFIG_PAYROLL_DISPUTE_APPROVAL_FLOW = "payroll.dispute.approval.flow";

    // ==================== 审批参数 ====================

    /**
     * 默认审批超时时间（小时）
     */
    public static final int DEFAULT_APPROVAL_TIMEOUT_HOURS = 24;

    /**
     * 最终审批超时时间（小时）
     */
    public static final int FINAL_APPROVAL_TIMEOUT_HOURS = 48;

}
