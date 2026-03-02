package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.enums.ApprovalStatus;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchConfirmRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollConfirmationAssignRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollConfirmationSummaryDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPendingConfirmationDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayslipConfirmRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayslipObjectionRequest;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import com.yiyundao.compensation.modules.user.entity.SysUser;

public interface PayrollConfirmationService {

    void confirmPayslip(Long lineId, SysUser currentUser, PayslipConfirmRequest request);

    Long objectPayslip(Long lineId, SysUser currentUser, PayslipObjectionRequest request);

    int batchConfirm(Long batchId, SysUser currentUser, PayrollBatchConfirmRequest request);

    int assignConfirmationAssignee(Long batchId, SysUser currentUser, PayrollConfirmationAssignRequest request);

    PayrollConfirmationSummaryDto getBatchSummary(Long batchId);

    Page<PayrollPendingConfirmationDto> pagePendingConfirmations(SysUser currentUser, Long batchId, int page, int size);

    void handleDisputeWorkflowCompleted(ApprovalWorkflow workflow, ApprovalStatus finalStatus);

    void refreshBatchConfirmationStatus(Long batchId);
}
