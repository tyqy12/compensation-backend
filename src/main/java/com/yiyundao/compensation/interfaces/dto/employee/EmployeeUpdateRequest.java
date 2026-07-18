package com.yiyundao.compensation.interfaces.dto.employee;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class EmployeeUpdateRequest {
    private String name;
    private String phone;
    private String email;
    private String idCard; // 明文，后端加密
    private String department;
    private List<String> departments;
    private String position;
    private String employmentType;
    private Long managerId;
    private LocalDate hireDate;
    private String status;
    // 收款账户类型: bank_card/alipay/wechat/other
    private String settlementAccountType;
    // 收款账户（明文，后端加密）
    private String settlementAccount;
    // 收款账户实名/户名
    private String settlementAccountName;
    private String bankAccount; // 明文，后端加密
    private String bankName;
    // 开户支行（银行卡场景）
    private String bankBranchName;
    private Boolean offline;
}
