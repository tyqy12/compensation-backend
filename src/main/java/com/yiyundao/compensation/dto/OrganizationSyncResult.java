package com.yiyundao.compensation.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrganizationSyncResult {

    private String platformType;
    private boolean success;
    private String message;
    private LocalDateTime syncTime;

    // 同步统计
    private int totalEmployees;
    private int newEmployees;
    private int updatedEmployees;
    private int inactiveEmployees;

    // 错误信息
    private List<String> errors;

    public static OrganizationSyncResult success(String platformType, int total, int newCount, int updateCount) {
        OrganizationSyncResult result = new OrganizationSyncResult();
        result.setPlatformType(platformType);
        result.setSuccess(true);
        result.setMessage("同步成功");
        result.setSyncTime(LocalDateTime.now());
        result.setTotalEmployees(total);
        result.setNewEmployees(newCount);
        result.setUpdatedEmployees(updateCount);
        return result;
    }

    public static OrganizationSyncResult failure(String platformType, String message, List<String> errors) {
        OrganizationSyncResult result = new OrganizationSyncResult();
        result.setPlatformType(platformType);
        result.setSuccess(false);
        result.setMessage(message);
        result.setSyncTime(LocalDateTime.now());
        result.setErrors(errors);
        return result;
    }
}