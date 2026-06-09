package com.yiyundao.compensation.interfaces.controller.payment;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PaymentType;
import com.yiyundao.compensation.modules.payment.dto.PaymentBatchResponse;
import com.yiyundao.compensation.interfaces.vo.payment.PaymentBatchVO;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.payment.dto.PaymentBatchTransferValidationDto;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payment.service.SettlementService;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import com.yiyundao.compensation.service.EncryptionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentBatchControllerTest {

    private final PaymentBatchService paymentBatchService = mock(PaymentBatchService.class);
    private final PaymentRecordService paymentRecordService = mock(PaymentRecordService.class);
    private final SettlementService settlementService = mock(SettlementService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
    private final EncryptionService encryptionService = mock(EncryptionService.class);
    private final PayrollBatchService payrollBatchService = mock(PayrollBatchService.class);
    private final PayrollPaymentService payrollPaymentService = mock(PayrollPaymentService.class);
    private final PaymentBatchController controller = new PaymentBatchController(
            paymentBatchService,
            paymentRecordService,
            settlementService,
            auditLogService,
            employeeMapper,
            encryptionService,
            payrollBatchService,
            payrollPaymentService
    );

    @Test
    void detailShouldReturnNotFoundWhenBatchMissing() {
        when(paymentBatchService.getByBatchNo("PB-404")).thenReturn(null);

        ApiResponse<PaymentBatchVO> response = controller.detail("PB-404");

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("批次不存在");
    }

    @Test
    void precheckShouldReturnNotFoundWhenBatchMissing() {
        when(paymentBatchService.getByBatchNo("PB-405")).thenReturn(null);

        ApiResponse<PaymentBatchTransferValidationDto> response = controller.precheck("PB-405", true);

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("批次不存在");
        verify(settlementService, never()).validateBatchForTransfer("PB-405", true);
    }

    @Test
    void precheckShouldDelegatePersistFailureFlag() {
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-409");
        PaymentBatchTransferValidationDto validation = PaymentBatchTransferValidationDto.builder()
                .batchNo("PB-409")
                .pendingCount(0)
                .passCount(0)
                .blockedCount(0)
                .pass(true)
                .warnings(List.of())
                .blockedRecords(List.of())
                .build();
        when(paymentBatchService.getByBatchNo("PB-409")).thenReturn(paymentBatch);
        when(settlementService.validateBatchForTransfer("PB-409", false)).thenReturn(validation);

        ApiResponse<PaymentBatchTransferValidationDto> response = controller.precheck("PB-409", false);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getBatchNo()).isEqualTo("PB-409");
        verify(settlementService).validateBatchForTransfer("PB-409", false);
    }

    @Test
    void startShouldRejectBatchStatusThatCannotStartTransfer() {
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-DRAFT");
        paymentBatch.setStatus(BatchStatus.DRAFT);
        when(paymentBatchService.getByBatchNo("PB-DRAFT")).thenReturn(paymentBatch);

        ApiResponse<String> response = controller.start("PB-DRAFT");

        assertThat(response.getCode()).isEqualTo(ErrorCode.INVALID_STATUS.getCode());
        assertThat(response.getMessage()).contains("状态不可启动转账");
        verify(settlementService, never()).validateBatchForTransfer(any(), any(boolean.class));
        verify(settlementService, never()).batchTransfer(any());
    }

    @Test
    void startShouldReturnNotFoundWhenBatchMissing() {
        when(paymentBatchService.getByBatchNo("PB-START-404")).thenReturn(null);

        ApiResponse<String> response = controller.start("PB-START-404");

        assertThat(response.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
        assertThat(response.getMessage()).contains("批次不存在");
        verify(settlementService, never()).validateBatchForTransfer(any(), any(boolean.class));
        verify(settlementService, never()).batchTransfer(any());
    }

    @Test
    void recordsShouldReturnNotFoundWhenBatchMissing() {
        when(paymentBatchService.getByBatchNo("PB-RECORDS-404")).thenReturn(null);

        var response = controller.records("PB-RECORDS-404", null);

        assertThat(response.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
        assertThat(response.getMessage()).contains("批次不存在");
        verify(paymentRecordService, never()).getByBatchNo(any(), any());
    }

    @Test
    void startShouldValidateAndDelegateWhenBatchCanStartTransfer() {
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-SUBMITTED");
        paymentBatch.setStatus(BatchStatus.SUBMITTED);
        PaymentBatchTransferValidationDto validation = PaymentBatchTransferValidationDto.builder()
                .batchNo("PB-SUBMITTED")
                .pendingCount(1)
                .passCount(1)
                .blockedCount(0)
                .pass(true)
                .warnings(List.of())
                .blockedRecords(List.of())
                .build();
        when(paymentBatchService.getByBatchNo("PB-SUBMITTED")).thenReturn(paymentBatch);
        when(settlementService.validateBatchForTransfer("PB-SUBMITTED", true)).thenReturn(validation);

        ApiResponse<String> response = controller.start("PB-SUBMITTED");

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isEqualTo("批量转账已启动");
        verify(settlementService).batchTransfer("PB-SUBMITTED");
    }

    @Test
    void startShouldExposeBatchLevelValidationWarning() {
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-STALE");
        paymentBatch.setStatus(BatchStatus.SUBMITTED);
        PaymentBatchTransferValidationDto validation = PaymentBatchTransferValidationDto.builder()
                .batchNo("PB-STALE")
                .pendingCount(0)
                .passCount(0)
                .blockedCount(0)
                .pass(false)
                .warnings(List.of("支付批次关联的薪资发放单已过期，禁止启动工资转账"))
                .blockedRecords(List.of())
                .build();
        when(paymentBatchService.getByBatchNo("PB-STALE")).thenReturn(paymentBatch);
        when(settlementService.validateBatchForTransfer("PB-STALE", true)).thenReturn(validation);

        ApiResponse<String> response = controller.start("PB-STALE");

        assertThat(response.getCode()).isEqualTo(ErrorCode.BUSINESS_ERROR.getCode());
        assertThat(response.getMessage()).contains("发放单已过期");
        assertThat(response.getMessage()).doesNotContain("0条风险记录");
        verify(settlementService, never()).batchTransfer("PB-STALE");
    }

    @Test
    void retryFailedShouldReturnNotFoundWhenPaymentBatchMissing() {
        when(paymentBatchService.getByBatchNo("PB-406")).thenReturn(null);

        ApiResponse<PaymentBatchResponse> response = controller.retryFailed("PB-406", true);

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("批次不存在");
        verify(payrollPaymentService, never()).retryFailedPayment(any(), any(boolean.class));
    }

    @Test
    void retryFailedShouldReturnNotFoundWhenPayrollBatchMissing() {
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-407");
        when(paymentBatchService.getByBatchNo("PB-407")).thenReturn(paymentBatch);
        when(payrollBatchService.getOne(any())).thenReturn(null);

        ApiResponse<PaymentBatchResponse> response = controller.retryFailed("PB-407", true);

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("未找到关联薪资批次");
        verify(payrollPaymentService, never()).retryFailedPayment(any(), any(boolean.class));
    }

    @Test
    void retryFailedShouldDelegateToPayrollRetryByLinkedPaymentBatchNo() {
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-408");
        PayrollBatch payrollBatch = new PayrollBatch();
        payrollBatch.setId(88L);
        payrollBatch.setPaymentBatchNo("PB-408");
        PaymentBatch retried = new PaymentBatch();
        retried.setId(99L);
        retried.setBatchNo("PB-408");
        retried.setPaymentType(PaymentType.SALARY);
        retried.setStatus(BatchStatus.PROCESSING);
        retried.setPaymentStatus(PaymentBatchProcessStatus.PROCESSING);

        when(paymentBatchService.getByBatchNo("PB-408")).thenReturn(paymentBatch);
        when(payrollBatchService.getOne(any())).thenReturn(payrollBatch);
        when(payrollPaymentService.retryFailedPayment(88L, true)).thenReturn(retried);

        ApiResponse<PaymentBatchResponse> response = controller.retryFailed("PB-408", true);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getBatchNo()).isEqualTo("PB-408");
        assertThat(response.getData().getStatus()).isEqualTo("processing");
        verify(payrollPaymentService).retryFailedPayment(88L, true);
    }

    @Test
    void recordsShouldRejectInvalidPaymentStatusFilter() {
        PaymentBatch paymentBatch = new PaymentBatch();
        paymentBatch.setBatchNo("PB-410");
        when(paymentBatchService.getByBatchNo("PB-410")).thenReturn(paymentBatch);

        assertThatThrownBy(() -> controller.records("PB-410", "unknown"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(ex.getMessage()).contains("无效的支付状态");
                });

        verify(paymentRecordService, never()).getByBatchNo(any(), any());
    }
}
