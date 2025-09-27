package com.yiyundao.compensation.interfaces.adapter;

import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.modules.employee.entity.Employee;

import java.util.List;

/**
 * 组织同步适配器接口（已迁移至 interfaces/adapter）
 */
public interface OrganizationAdapter {

    String getPlatformType();

    OrganizationSyncResult syncOrganization();

    Employee getUserInfo(String platformUserId);

    boolean isManager(String platformUserId);

    List<Employee> getDepartmentEmployees(String departmentId);

    void sendApprovalNotification(String platformUserId, String message);

    boolean checkConnection();
}

