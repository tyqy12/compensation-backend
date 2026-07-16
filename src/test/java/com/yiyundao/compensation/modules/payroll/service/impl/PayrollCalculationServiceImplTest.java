package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.infrastructure.dao.EmployeeMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollImportItemMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollImportItem;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.entity.SalaryItem;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplate;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.SalaryItemService;
import com.yiyundao.compensation.modules.payroll.service.SalaryTemplateService;
import com.yiyundao.compensation.modules.payroll.service.SalaryTemplateVersionService;
import com.yiyundao.compensation.modules.payroll.support.PayrollValidationIssueSupport;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollCalculationServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PayrollLine.class);
        initTableInfo(SalaryItem.class);
        initTableInfo(SalaryTemplate.class);
        initTableInfo(PayrollImportItem.class);
    }

    private static void initTableInfo(Class<?> entityClass) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }

    @Mock
    private PayrollBatchMapper payrollBatchMapper;
    @Mock
    private PayrollImportItemMapper importItemMapper;
    @Mock
    private SalaryItemService salaryItemService;
    @Mock
    private SalaryTemplateService salaryTemplateService;
    @Mock
    private SalaryTemplateVersionService salaryTemplateVersionService;
    @Mock
    private PayrollLineService payrollLineService;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private PayrollValidationIssueSupport validationIssueSupport;
    @Mock
    private PayrollCalculationFailureMarker calculationFailureMarker;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void computeAndSaveShouldMarkCalculationFailedAfterTransactionRollback() {
        PayrollCalculationServiceImpl service = newService();
        PayrollBatch batch = stubSuccessfulCalculationInputs(42L);
        LocalDateTime originalUpdateTime = LocalDateTime.parse("2026-06-05T12:00:00");
        batch.setUpdateTime(originalUpdateTime);
        batch.setVersion(7);
        doAnswer(invocation -> {
            PayrollBatch updated = invocation.getArgument(0);
            updated.setVersion(8);
            return 1;
        }).when(payrollBatchMapper).updateById(any(PayrollBatch.class));
        when(payrollLineService.saveOrUpdateBatch(any())).thenThrow(new RuntimeException("persist failed"));

        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> service.computeAndSave(42L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("persist failed");

        verify(calculationFailureMarker, never()).markFailed(eq(42L), any(), any());
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        synchronizations.forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(calculationFailureMarker).markFailed(42L, originalUpdateTime, 7);
    }

    @Test
    void computeAndSaveShouldMarkCalculationFailedImmediatelyWithoutTransactionSynchronization() {
        PayrollCalculationServiceImpl service = newService();
        PayrollBatch batch = stubSuccessfulCalculationInputs(43L);
        LocalDateTime originalUpdateTime = LocalDateTime.parse("2026-06-05T12:00:00");
        batch.setUpdateTime(originalUpdateTime);
        batch.setVersion(7);
        doAnswer(invocation -> {
            PayrollBatch updated = invocation.getArgument(0);
            updated.setVersion(8);
            return 1;
        }).when(payrollBatchMapper).updateById(any(PayrollBatch.class));
        when(payrollLineService.saveOrUpdateBatch(any())).thenThrow(new RuntimeException("persist failed"));

        assertThatThrownBy(() -> service.computeAndSave(43L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("persist failed");

        ArgumentCaptor<LocalDateTime> updateTimeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(calculationFailureMarker).markFailed(eq(43L), updateTimeCaptor.capture(), eq(8));
        assertThat(updateTimeCaptor.getValue()).isAfter(originalUpdateTime);
    }

    @Test
    void computeAndSaveShouldStopBeforeWritingLinesWhenMarkCalculatingConflicts() {
        PayrollCalculationServiceImpl service = newService();
        stubReadyInputsUntilCalculating(44L);
        when(payrollBatchMapper.updateById(any(PayrollBatch.class))).thenReturn(0);

        assertThatThrownBy(() -> service.computeAndSave(44L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REQUEST_CONFLICT);
                    assertThat(ex.getMessage()).contains("批次状态已变更");
                });

        verify(payrollLineService, never()).remove(any());
        verify(payrollLineService, never()).saveOrUpdateBatch(any());
        verify(calculationFailureMarker, never()).markFailed(eq(44L), any(), any());
    }

    @Test
    void computeAndSaveShouldStopBeforeWritingLinesWhenBlockingIssuesExist() {
        PayrollCalculationServiceImpl service = newService(new PayrollValidationIssueSupport(new com.fasterxml.jackson.databind.ObjectMapper()));
        PayrollBatch batch = stubReadyInputsUntilCalculating(45L);

        assertThatThrownBy(() -> service.computeAndSave(45L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.getMessage()).contains("未配置适用于当前批次类型的启用薪资模板");
        });

        verify(payrollBatchMapper, never()).updateById(any(PayrollBatch.class));
        verify(payrollLineService, never()).remove(any());
        verify(payrollLineService, never()).saveOrUpdateBatch(any());
        verify(calculationFailureMarker, never()).markFailed(eq(45L), any(), any());
        assertThat(batch.getStatus()).isEqualTo(PayrollBatchStatus.LOCKED);
    }

    private PayrollCalculationServiceImpl newService() {
        return newService(validationIssueSupport);
    }

    private PayrollCalculationServiceImpl newService(PayrollValidationIssueSupport issueSupport) {
        return new PayrollCalculationServiceImpl(
                payrollBatchMapper,
                importItemMapper,
                salaryItemService,
                salaryTemplateService,
                salaryTemplateVersionService,
                payrollLineService,
                employeeMapper,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                issueSupport,
                calculationFailureMarker
        );
    }

    private PayrollBatch stubSuccessfulCalculationInputs(Long batchId) {
        PayrollBatch batch = stubReadyInputsUntilCalculating(batchId);
        when(payrollLineService.remove(any())).thenReturn(true);
        when(validationIssueSupport.toMessages(any())).thenReturn(List.of());
        when(validationIssueSupport.serialize(any())).thenReturn("[]");
        return batch;
    }

    private PayrollBatch stubReadyInputsUntilCalculating(Long batchId) {
        PayrollBatch batch = new PayrollBatch();
        batch.setId(batchId);
        batch.setStatus(PayrollBatchStatus.LOCKED);
        batch.setBatchRevision(1);
        batch.setType("full_time");
        batch.setCurrency("CNY");
        when(payrollBatchMapper.selectById(batchId)).thenReturn(batch);

        SalaryItem salaryItem = new SalaryItem();
        salaryItem.setCode("BASIC");
        salaryItem.setName("基本工资");
        salaryItem.setType("earning");
        salaryItem.setTaxable(Boolean.TRUE);
        salaryItem.setStatus("enabled");
        when(salaryItemService.list(org.mockito.ArgumentMatchers.<Wrapper<SalaryItem>>any()))
                .thenReturn(List.of(salaryItem));
        when(salaryTemplateService.list(org.mockito.ArgumentMatchers.<Wrapper<SalaryTemplate>>any()))
                .thenReturn(List.of());

        PayrollImportItem importItem = new PayrollImportItem();
        importItem.setBatchId(batchId);
        importItem.setEmployeeId(101L);
        importItem.setItemCode("BASIC");
        importItem.setAmount(new BigDecimal("1000.00"));
        importItem.setStatus("valid");
        when(importItemMapper.selectList(any())).thenReturn(List.of(importItem));

        Employee employee = new Employee();
        employee.setId(101L);
        employee.setEmployeeId("E001");
        employee.setName("测试员工");
        employee.setEmploymentType("full_time");
        when(employeeMapper.selectBatchIds(any())).thenReturn(List.of(employee));

        when(payrollLineService.list(org.mockito.ArgumentMatchers.<Wrapper<PayrollLine>>any()))
                .thenReturn(List.of());
        return batch;
    }
}
