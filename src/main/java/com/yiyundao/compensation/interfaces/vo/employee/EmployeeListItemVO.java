package com.yiyundao.compensation.interfaces.vo.employee;

import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.enums.EmployeeStatus;
import com.yiyundao.compensation.enums.PlatformType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmployeeListItemVO {
    private Long id;
    private String employeeId;
    private String name;
    private String department;
    private String position;
    private String status;
    private String statusName;
    private String platformType;
    private String platformTypeName;
    private Boolean offline;
    private LocalDate hireDate;
    private LocalDateTime createTime;

    public static EmployeeListItemVO from(Employee e) {
        EmployeeListItemVO vo = new EmployeeListItemVO();
        vo.setId(e.getId());
        vo.setEmployeeId(e.getEmployeeId());
        vo.setName(e.getName());
        vo.setDepartment(e.getDepartment());
        vo.setPosition(e.getPosition());
        vo.setStatus(e.getStatus());
        vo.setStatusName(translateStatus(e.getStatus()));
        vo.setPlatformType(e.getPlatformType());
        vo.setPlatformTypeName(translatePlatform(e.getPlatformType()));
        vo.setOffline(e.getOffline());
        vo.setHireDate(e.getHireDate());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }

    private static String translateStatus(String code) {
        if (code == null) return null;
        try {
            return EmployeeStatus.fromCode(code).getName();
        } catch (Exception ignored) {
            return code;
        }
    }

    private static String translatePlatform(String code) {
        if (code == null) return null;
        try {
            return PlatformType.fromCode(code).getName();
        } catch (Exception ignored) {
            return code;
        }
    }
}
