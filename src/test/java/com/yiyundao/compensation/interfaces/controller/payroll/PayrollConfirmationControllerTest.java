package com.yiyundao.compensation.interfaces.controller.payroll;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBatchConfirmRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollConfirmationAssignRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollConfirmationSummaryDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPendingConfirmationDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayslipConfirmRequest;
import com.yiyundao.compensation.interfaces.dto.payroll.PayslipObjectionRequest;
import com.yiyundao.compensation.modules.payroll.service.PayrollConfirmationService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollConfirmationControllerTest {

    @Mock
    private PayrollConfirmationService payrollConfirmationService;
    @Mock
    private SysUserService sysUserService;

    private PayrollConfirmationController controller;
    private SysUser currentUser;

    @BeforeEach
    void setUp() {
        controller = new PayrollConfirmationController(payrollConfirmationService, sysUserService);
        currentUser = new SysUser();
        currentUser.setId(10L);
        currentUser.setUsername("alice");
        currentUser.setEmployeeId(100L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "n/a")
        );
        lenient().when(sysUserService.findByUsername("alice")).thenReturn(currentUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void confirmPayslip_shouldDelegateToServiceWithCurrentUser() {
        PayslipConfirmRequest request = new PayslipConfirmRequest();
        request.setSignature("Alice");
        request.setComment("已确认");

        ApiResponse<Boolean> response = controller.confirmPayslip(1L, request);

        verify(payrollConfirmationService).confirmPayslip(1L, currentUser, request);
        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isTrue();
    }

    @Test
    void objectPayslip_shouldReturnWorkflowId() {
        PayslipObjectionRequest request = new PayslipObjectionRequest();
        request.setReason("金额异常");
        request.setComment("请复核");
        when(payrollConfirmationService.objectPayslip(2L, currentUser, request)).thenReturn(999L);

        ApiResponse<Map<String, Object>> response = controller.objectPayslip(2L, request);

        verify(payrollConfirmationService).objectPayslip(2L, currentUser, request);
        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).containsEntry("workflowId", 999L);
    }

    @Test
    void batchConfirm_shouldReturnAffectedCount() {
        PayrollBatchConfirmRequest request = new PayrollBatchConfirmRequest();
        request.setSignature("Leader");
        when(payrollConfirmationService.batchConfirm(3L, currentUser, request)).thenReturn(6);

        ApiResponse<Map<String, Object>> response = controller.batchConfirm(3L, request);

        verify(payrollConfirmationService).batchConfirm(3L, currentUser, request);
        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).containsEntry("affected", 6);
    }

    @Test
    void assign_shouldReturnAffectedCount() {
        PayrollConfirmationAssignRequest request = new PayrollConfirmationAssignRequest();
        request.setAssigneeEmployeeId(200L);
        request.setApplyAll(true);
        when(payrollConfirmationService.assignConfirmationAssignee(4L, currentUser, request)).thenReturn(12);

        ApiResponse<Map<String, Object>> response = controller.assign(4L, request);

        verify(payrollConfirmationService).assignConfirmationAssignee(4L, currentUser, request);
        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).containsEntry("affected", 12);
    }

    @Test
    void pending_shouldReturnPageData() {
        Page<PayrollPendingConfirmationDto> page = new Page<>(1, 10, 1);
        PayrollPendingConfirmationDto dto = new PayrollPendingConfirmationDto();
        dto.setLineId(1000L);
        page.setRecords(List.of(dto));
        when(payrollConfirmationService.pagePendingConfirmations(currentUser, 5L, 1, 10)).thenReturn(page);

        ApiResponse<Page<PayrollPendingConfirmationDto>> response = controller.pending(1, 10, 5L);

        verify(payrollConfirmationService).pagePendingConfirmations(currentUser, 5L, 1, 10);
        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getRecords()).hasSize(1);
        assertThat(response.getData().getRecords().get(0).getLineId()).isEqualTo(1000L);
    }

    @Test
    void summary_shouldReturnSummaryData() {
        PayrollConfirmationSummaryDto summary = new PayrollConfirmationSummaryDto();
        summary.setBatchId(6L);
        summary.setPendingCount(2L);
        when(payrollConfirmationService.getBatchSummary(6L)).thenReturn(summary);

        ApiResponse<PayrollConfirmationSummaryDto> response = controller.summary(6L);

        verify(payrollConfirmationService).getBatchSummary(6L);
        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getBatchId()).isEqualTo(6L);
        assertThat(response.getData().getPendingCount()).isEqualTo(2L);
    }

    @Test
    void assignShouldRequireFinanceOrAdmin() throws NoSuchMethodException {
        Method method = PayrollConfirmationController.class.getMethod(
                "assign", Long.class, PayrollConfirmationAssignRequest.class);

        assertThat(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrAdmin.class)).isTrue();
    }

    @Test
    void summaryShouldRequireFinanceHrOrAdmin() throws NoSuchMethodException {
        Method method = PayrollConfirmationController.class.getMethod("summary", Long.class);

        assertThat(method.isAnnotationPresent(SecurityAnnotations.IsFinanceOrHrOrAdmin.class)).isTrue();
    }
}
