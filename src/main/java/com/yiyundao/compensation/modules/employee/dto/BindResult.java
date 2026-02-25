package com.yiyundao.compensation.modules.employee.dto;

/**
 * 平台绑定结果枚举
 * <p>
 * 用于标识平台绑定操作的不同结果状态，便于前端展示和追溯。
 * </p>
 *
 * @author 芙宁娜
 * @since 2026-01-28
 */
public enum BindResult {

    /**
     * 绑定成功
     */
    SUCCESS("绑定成功"),

    /**
     * 已是同一账号（无需重复绑定）
     */
    ALREADY_BOUND("已是同一平台账号，无需重复绑定"),

    /**
     * 冲突，等待审批
     */
    PENDING_APPROVAL("平台账号冲突，已发起审批流程"),

    /**
     * 员工不存在
     */
    EMPLOYEE_NOT_FOUND("员工不存在"),

    /**
     * 平台账号冲突，无法绑定
     */
    PLATFORM_CONFLICT("平台账号已被其他员工占用"),

    /**
     * 员工已绑定其他账号
     */
    EMPLOYEE_BOUND_OTHER("该员工已绑定其他平台账号"),

    /**
     * 审批已拒绝
     */
    APPROVAL_REJECTED("审批已被拒绝"),

    /**
     * 审批已撤销
     */
    APPROVAL_CANCELLED("审批已撤销"),

    /**
     * 未知错误
     */
    UNKNOWN_ERROR("未知错误");

    private final String description;

    BindResult(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为成功状态
     */
    public boolean isSuccess() {
        return this == SUCCESS || this == ALREADY_BOUND;
    }

    /**
     * 判断是否需要等待审批
     */
    public boolean isPending() {
        return this == PENDING_APPROVAL;
    }

    /**
     * 判断是否为最终结果（无需后续操作）
     */
    public boolean isFinal() {
        return isSuccess() || this == APPROVAL_REJECTED || this == APPROVAL_CANCELLED || this == PLATFORM_CONFLICT || this == EMPLOYEE_NOT_FOUND;
    }
}
