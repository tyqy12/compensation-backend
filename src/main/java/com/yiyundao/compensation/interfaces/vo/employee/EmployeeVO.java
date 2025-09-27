package com.yiyundao.compensation.interfaces.vo.employee;

import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.service.EncryptionService;
import lombok.Data;

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
    private String platformUserId;
    private String platformType;
    private Long managerId;
    private LocalDate hireDate;
    private String status;
    private String bankAccountMasked;
    private String bankName;
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
        vo.setPlatformUserId(e.getPlatformUserId());
        vo.setPlatformType(e.getPlatformType());
        vo.setManagerId(e.getManagerId());
        vo.setHireDate(e.getHireDate());
        vo.setStatus(e.getStatus());
        // 银行卡号在存储前已加密，这里不直接解密展示，避免泄露风险
        vo.setBankAccountMasked(null);
        vo.setBankName(e.getBankName());
        vo.setOffline(e.getOffline());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }
}
