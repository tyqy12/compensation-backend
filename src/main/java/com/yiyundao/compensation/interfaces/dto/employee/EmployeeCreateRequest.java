package com.yiyundao.compensation.interfaces.dto.employee;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class EmployeeCreateRequest {
    @NotBlank
    private String employeeId;
    @NotBlank
    private String name;

    private String phone;
    private String email;
    // 明文身份证号，后端负责加密存储
    private String idCard;

    private String department;
    private List<String> departments;
    private String position;
    // 用工类型: full_time / part_time
    private String employmentType;
    private String subjectId;
    private String provider;
    @JsonIgnore
    private String legacyPlatformType;
    @JsonIgnore
    private String legacyPlatformUserId;
    private Long managerId;
    private LocalDate hireDate;
    private String status; // 默认 active
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
    // 可选：指定要创建的系统用户名（若冲突会自动追加数字）
    private String username;

    @JsonAnySetter
    public void captureLegacyPlatformFields(String key, Object value) {
        if (value == null || key == null) {
            return;
        }
        if ("platformType".equals(key)) {
            this.legacyPlatformType = String.valueOf(value);
            return;
        }
        if ("platformUserId".equals(key)) {
            this.legacyPlatformUserId = String.valueOf(value);
        }
    }
}
