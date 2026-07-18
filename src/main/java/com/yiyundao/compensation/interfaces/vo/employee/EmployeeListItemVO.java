package com.yiyundao.compensation.interfaces.vo.employee;

import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.enums.EmployeeStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Data
public class EmployeeListItemVO {
    private Long id;
    private String employeeId;
    private String name;
    private String department;
    private List<String> departments;
    private String position;
    private String status;
    private String statusName;
    private String provider;
    private String providerName;
    private Boolean offline;
    private LocalDate hireDate;
    private LocalDateTime createTime;

    public static EmployeeListItemVO from(Employee e) {
        EmployeeListItemVO vo = new EmployeeListItemVO();
        vo.setId(e.getId());
        vo.setEmployeeId(e.getEmployeeId());
        vo.setName(e.getName());
        vo.setDepartment(e.getDepartment());
        vo.setDepartments(splitDepartments(e.getDepartment()));
        vo.setPosition(e.getPosition());
        vo.setStatus(e.getStatus());
        vo.setStatusName(translateStatus(e.getStatus()));
        vo.setProvider(e.getProvider());
        vo.setProviderName(translatePlatform(e.getProvider()));
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
        return switch (code) {
            case "wechat" -> "企业微信";
            case "dingtalk" -> "钉钉";
            case "feishu" -> "飞书";
            default -> code;
        };
    }

    private static List<String> splitDepartments(String department) {
        if (department == null || department.isBlank()) {
            return List.of();
        }
        return Arrays.stream(department.split("[,/，、]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
