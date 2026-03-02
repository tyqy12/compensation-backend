package com.yiyundao.compensation.modules.payment.service;

import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payment.entity.EmployeeTypeProviderMapping;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;

import java.util.List;

/**
 * 结算渠道路由服务
 */
public interface SettlementProviderRoutingService {

    /**
     * 为员工确定结算渠道
     * 优先级：员工配置 > 批次配置 > 员工类型映射
     *
     * @param employee 员工信息
     * @param batch 薪酬批次（可选）
     * @return 渠道代码
     */
    String determineProvider(Employee employee, PayrollBatch batch);

    /**
     * 配置员工类型与渠道的映射关系
     */
    EmployeeTypeProviderMapping createMapping(EmploymentType employmentType, String providerCode, Integer priority);

    /**
     * 更新员工类型映射
     */
    EmployeeTypeProviderMapping updateMapping(Long id, String providerCode, Integer priority);

    /**
     * 删除员工类型映射
     */
    void deleteMapping(Long id);

    /**
     * 获取指定员工类型的所有映射（按优先级排序）
     */
    List<EmployeeTypeProviderMapping> getMappingsByEmploymentType(EmploymentType employmentType);

    /**
     * 获取所有映射
     */
    List<EmployeeTypeProviderMapping> getAllMappings();

    /**
     * 启用/禁用映射
     */
    void toggleMappingStatus(Long id, Boolean enabled);
}
