package com.yiyundao.compensation.modules.employee.dto;

import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Data
public class EmployeeProfileChangePayload {

    private String name;
    private String idCard;
    private String settlementAccountType;
    private String settlementAccount;
    private String settlementAccountName;
    private String bankAccount;
    private String bankName;
    private String bankBranchName;

    public boolean hasAnyChangeField() {
        return StringUtils.hasText(name)
                || StringUtils.hasText(idCard)
                || StringUtils.hasText(settlementAccountType)
                || StringUtils.hasText(settlementAccount)
                || StringUtils.hasText(settlementAccountName)
                || StringUtils.hasText(bankAccount)
                || StringUtils.hasText(bankName)
                || StringUtils.hasText(bankBranchName);
    }

    public List<String> changedFields() {
        List<String> fields = new ArrayList<>();
        if (StringUtils.hasText(name)) fields.add("name");
        if (StringUtils.hasText(idCard)) fields.add("idCard");
        if (StringUtils.hasText(settlementAccountType)) fields.add("settlementAccountType");
        if (StringUtils.hasText(settlementAccount)) fields.add("settlementAccount");
        if (StringUtils.hasText(settlementAccountName)) fields.add("settlementAccountName");
        if (StringUtils.hasText(bankAccount)) fields.add("bankAccount");
        if (StringUtils.hasText(bankName)) fields.add("bankName");
        if (StringUtils.hasText(bankBranchName)) fields.add("bankBranchName");
        return fields;
    }

    public EmployeeProfileChangePayload normalize() {
        if (name != null) {
            name = name.trim();
        }
        if (idCard != null) {
            idCard = idCard.trim();
        }
        if (settlementAccountType != null) {
            settlementAccountType = settlementAccountType.trim();
        }
        if (settlementAccount != null) {
            settlementAccount = settlementAccount.trim();
        }
        if (settlementAccountName != null) {
            settlementAccountName = settlementAccountName.trim();
        }
        if (bankAccount != null) {
            bankAccount = bankAccount.trim();
        }
        if (bankName != null) {
            bankName = bankName.trim();
        }
        if (bankBranchName != null) {
            bankBranchName = bankBranchName.trim();
        }
        return this;
    }
}
