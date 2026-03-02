package com.yiyundao.compensation.modules.employee.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.enums.EmployeeStatus;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeListItemVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeApprovalRecordVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeePayslipRecordVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeVO;
import com.yiyundao.compensation.interfaces.vo.payment.PaymentRecordItemVO;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformRequest;
import com.yiyundao.compensation.modules.employee.dto.BindPlatformResult;
import com.yiyundao.compensation.modules.employee.entity.Employee;

import java.util.List;

public interface EmployeeService extends IService<Employee> {
    EmployeeVO createEmployee(Employee employee);
    EmployeeVO createEmployeeWithUser(Employee employee, String username);
    EmployeeVO updateEmployee(Long id, Employee updateInfo);
    EmployeeVO getEmployeeVO(Long id);
    PageResponse<EmployeeApprovalRecordVO> pageEmployeeApprovals(Long employeeId, int pageNum, int pageSize);
    PageResponse<EmployeePayslipRecordVO> pageEmployeePayslips(Long employeeId, int pageNum, int pageSize);
    PageResponse<PaymentRecordItemVO> pageEmployeePayments(Long employeeId, int pageNum, int pageSize);
    PageResponse<EmployeeListItemVO> pageEmployees(int pageNum, int pageSize, String keyword,
                                                   String department, String status,
                                                   Boolean isOffline, String platformType,
                                                   Long managerId, String sortBy, String order);
    List<EmployeeVO> getOfflineEmployees(Long managerId);

    /**
     * 绑定平台用户（统一入口，支持冲突检测和审批流程）
     * <p>
     * 一个员工只能绑定一个平台账号，冲突时自动发起审批流程。
     * </p>
     *
     * @param employeeId 员工ID
     * @param request    绑定请求
     * @return 绑定结果（包含审批信息用于追溯）
     */
    BindPlatformResult bindPlatform(Long employeeId, BindPlatformRequest request);

    /**
     * 解绑平台用户（仅Admin可操作）
     *
     * @param employeeId 员工ID
     * @param reason     解绑原因（用于审计）
     */
    void unbindPlatform(Long employeeId, String reason);

    void updateStatus(Long employeeId, EmployeeStatus status);
    void batchImport(List<Employee> employees);
    String getDecryptedIdCard(Long employeeId);
    String getDecryptedBankAccount(Long employeeId);
    String getDecryptedSettlementAccount(Long employeeId);
    Employee getByPlatformUserId(String platformUserId, String platformType);
    Employee getByEmployeeId(String employeeId);
    boolean existsByEmployeeId(String employeeId);
    void setOfflineManager(Long employeeId, Long managerId);

    /**
     * 执行审批通过后的绑定操作
     * <p>
     * 此方法由审批流程完成后调用，完成实际的绑定操作。
     * </p>
     *
     * @param workflowId 审批流程ID
     * @param employeeId 员工ID
     * @param platformType 平台类型
     * @param platformUserId 平台用户ID
     * @return 绑定结果
     */
    BindPlatformResult executeApprovedBinding(Long workflowId, Long employeeId,
                                               String platformType, String platformUserId);
}
