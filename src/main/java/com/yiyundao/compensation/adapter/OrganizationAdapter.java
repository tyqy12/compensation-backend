package com.yiyundao.compensation.adapter;

import com.yiyundao.compensation.dto.OrganizationSyncResult;
import com.yiyundao.compensation.modules.employee.entity.Employee;

import java.util.List;

/**
 * 组织同步适配器接口
 * 支持多平台组织架构同步
 */
public interface OrganizationAdapter {

    /**
     * 获取平台类型
     */
    String getPlatformType();

    /**
     * 同步组织架构
     */
    OrganizationSyncResult syncOrganization();

    /**
     * 获取用户信息
     */
    Employee getUserInfo(String platformUserId);

    /**
     * 验证用户是否为管理员
     */
    boolean isManager(String platformUserId);

    /**
     * 获取部门员工列表
     */
    List<Employee> getDepartmentEmployees(String departmentId);

    /**
     * 发送审批通知
     */
    void sendApprovalNotification(String platformUserId, String message);

    /**
     * 检查平台连接状态
     */
    boolean checkConnection();
}
