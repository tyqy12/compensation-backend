package com.yiyundao.compensation.interfaces.dto.employee;

import lombok.Data;

@Data
public class EmployeeSelfSensitiveChangeRequest {

    private String name;
    private String idCard;

    private String settlementAccountType;
    private String settlementAccount;
    private String settlementAccountName;
    private String bankAccount;
    private String bankName;
    private String bankBranchName;

    private String reason;
}
