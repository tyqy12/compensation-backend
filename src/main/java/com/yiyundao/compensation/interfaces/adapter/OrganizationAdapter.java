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

    /**
     * 获取平台当前所有员工的预览数据（不落库，仅用于前端确认与编辑后导入）。
     */
    java.util.List<com.yiyundao.compensation.modules.employee.entity.Employee> fetchAllEmployees();

    /**
     * 获取平台的部门树（包含部门节点与成员列表，不落库）。
     */
    java.util.List<com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto> fetchDepartmentTree();

    Employee getUserInfo(String platformUserId);

    boolean isManager(String platformUserId);

    List<Employee> getDepartmentEmployees(String departmentId);

    void sendApprovalNotification(String platformUserId, String message);

    boolean checkConnection();
}
