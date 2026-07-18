package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum WorkflowType {
    BATCH("BATCH", "批量支付"),
    PAYROLL_DISTRIBUTION("PAYROLL_DISTRIBUTION", "薪资发放"),
    ADHOC("ADHOC", "临时支付"),
    OFFLINE("OFFLINE", "架构外员工"),
    EMPLOYEE_PROFILE_CHANGE("EMPLOYEE_PROFILE_CHANGE", "员工资料变更"),
    PLATFORM_BIND("PLATFORM_BIND", "平台账号绑定"),
    PERMISSION("PERMISSION", "权限授权"),
    PAYROLL_DISPUTE("PAYROLL_DISPUTE", "薪酬异议审批");

    @EnumValue
    private final String code;
    private final String name;

    WorkflowType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static WorkflowType fromCode(String code) {
        for (WorkflowType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown workflow type: " + code);
    }
}
