package com.yiyundao.compensation.interfaces.vo.employee;

import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.service.EncryptionService;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmployeeVO {
    private Long id;
    private String employeeId;
    private String name;
    private String phoneMasked;
    private String email;
    private String department;
    private String position;
    private String provider;
    private String subjectId;
    private String employmentType;
    private Long managerId;
    private String managerName;
    private LocalDate hireDate;
    private String status;
    private String settlementAccountType;
    private String settlementAccountTypeName;
    private String settlementAccountMasked;
    private String settlementAccountName;
    private String bankAccountMasked;
    private String bankName;
    private String bankBranchName;
    private Boolean offline;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static EmployeeVO from(Employee e, EncryptionService enc) {
        EmployeeVO vo = new EmployeeVO();
        vo.setId(e.getId());
        vo.setEmployeeId(e.getEmployeeId());
        vo.setName(e.getName());
        vo.setPhoneMasked(enc.maskPhone(e.getPhone()));
        vo.setEmail(e.getEmail());
        vo.setDepartment(e.getDepartment());
        vo.setPosition(e.getPosition());
        vo.setProvider(e.getProvider());
        vo.setSubjectId(e.getSubjectId());
        vo.setEmploymentType(e.getEmploymentType());
        vo.setManagerId(e.getManagerId());
        vo.setHireDate(e.getHireDate());
        vo.setStatus(e.getStatus());
        vo.setSettlementAccountType(e.getSettlementAccountType());
        vo.setSettlementAccountTypeName(translateSettlementAccountTypeName(e.getSettlementAccountType()));
        vo.setSettlementAccountName(e.getSettlementAccountName());
        // 银行卡号在存储前已加密，这里不直接解密展示，避免泄露风险
        vo.setBankAccountMasked(null);
        vo.setBankName(e.getBankName());
        vo.setBankBranchName(e.getBankBranchName());
        vo.setOffline(e.getOffline());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }

    private static String translateSettlementAccountTypeName(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return switch (code.trim().toLowerCase()) {
            case "bank_card" -> "银行卡";
            case "alipay" -> "支付宝";
            case "wechat" -> "微信";
            case "other" -> "其他";
            default -> code;
        };
    }
}
