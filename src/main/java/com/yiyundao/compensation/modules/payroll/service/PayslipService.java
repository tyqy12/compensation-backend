package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.interfaces.dto.payroll.EmployeePayslipDto;
import com.yiyundao.compensation.modules.user.entity.SysUser;

public interface PayslipService {

    Page<EmployeePayslipDto.PayslipSummary> pagePayslips(SysUser currentUser, Long employeeId, int page, int size);

    EmployeePayslipDto.PayslipDetail getPayslipDetail(SysUser currentUser, Long lineId);

    byte[] exportPayslip(SysUser currentUser, Long lineId);
}
