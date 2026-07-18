package com.yiyundao.compensation.modules.employee.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.common.utils.VOConverter;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.infrastructure.dao.ApprovalWorkflowMapper;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeePayslipRecordVO;
import com.yiyundao.compensation.modules.employee.service.impl.EmployeeServiceImpl;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.service.EncryptionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeServiceImplPayslipRecordTest {

    @Test
    void pageEmployeePayslipsShouldMapLineBatchAndCycleFields() {
        PayrollLineService payrollLineService = mock(PayrollLineService.class);
        PayrollBatchService payrollBatchService = mock(PayrollBatchService.class);
        PayCycleService payCycleService = mock(PayCycleService.class);
        EmployeeServiceImpl service = service(payrollLineService, payrollBatchService, payCycleService);

        PayrollLine line = new PayrollLine();
        line.setId(101L);
        line.setBatchId(201L);
        line.setEmployeeId(301L);
        line.setEmploymentType("full_time");
        line.setCurrency("CNY");
        line.setGrossAmount(new BigDecimal("12000.00"));
        line.setTaxAmount(new BigDecimal("800.00"));
        line.setSocialAmount(new BigDecimal("1200.00"));
        line.setNetAmount(new BigDecimal("10000.00"));
        line.setStatus("calculated");
        line.setCreateTime(LocalDateTime.parse("2026-06-01T10:00:00"));
        line.setUpdateTime(LocalDateTime.parse("2026-06-02T10:00:00"));

        when(payrollLineService.page(any(Page.class), any())).thenAnswer(invocation -> {
            Page<PayrollLine> page = invocation.getArgument(0);
            page.setRecords(List.of(line));
            page.setTotal(1);
            return page;
        });

        PayrollBatch batch = new PayrollBatch();
        batch.setId(201L);
        batch.setPayCycleId(401L);
        batch.setPeriodLabel("2026-06");
        batch.setStatus(PayrollBatchStatus.APPROVED);
        batch.setPaymentBatchNo("PB202606001");
        when(payrollBatchService.listByIds(argThat(ids -> containsOnly(ids, 201L)))).thenReturn(List.of(batch));

        PayCycle cycle = new PayCycle();
        cycle.setId(401L);
        cycle.setStartDate(LocalDate.of(2026, 6, 1));
        cycle.setEndDate(LocalDate.of(2026, 6, 30));
        when(payCycleService.listByIds(argThat(ids -> containsOnly(ids, 401L)))).thenReturn(List.of(cycle));

        PageResponse<EmployeePayslipRecordVO> response = service.pageEmployeePayslips(301L, 0, 1000);

        assertThat(response.getPageNum()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(200);
        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getList()).hasSize(1);

        EmployeePayslipRecordVO record = response.getList().get(0);
        assertThat(record.getLineId()).isEqualTo(101L);
        assertThat(record.getBatchId()).isEqualTo(201L);
        assertThat(record.getPayCycleId()).isEqualTo(401L);
        assertThat(record.getPeriodLabel()).isEqualTo("2026-06");
        assertThat(record.getPeriodStart()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(record.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(record.getBatchStatus()).isEqualTo("approved");
        assertThat(record.getPaymentBatchNo()).isEqualTo("PB202606001");
        assertThat(record.getEmploymentType()).isEqualTo("full_time");
        assertThat(record.getCurrency()).isEqualTo("CNY");
        assertThat(record.getGrossAmount()).isEqualByComparingTo("12000.00");
        assertThat(record.getTaxAmount()).isEqualByComparingTo("800.00");
        assertThat(record.getSocialAmount()).isEqualByComparingTo("1200.00");
        assertThat(record.getNetAmount()).isEqualByComparingTo("10000.00");
        assertThat(record.getStatus()).isEqualTo("calculated");

        ArgumentCaptor<Page<PayrollLine>> pageCaptor = ArgumentCaptor.captor();
        verify(payrollLineService).page(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200);
    }

    private static EmployeeServiceImpl service(PayrollLineService payrollLineService,
                                               PayrollBatchService payrollBatchService,
                                               PayCycleService payCycleService) {
        return new EmployeeServiceImpl(
                mock(EncryptionService.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(SysUserService.class),
                mock(ExternalIdentityService.class),
                mock(ApprovalWorkflowMapper.class),
                payrollLineService,
                payrollBatchService,
                payCycleService,
                mock(PaymentRecordService.class),
                mock(VOConverter.class),
                new ObjectMapper(),
                mock(EmployeeDepartmentService.class)
        );
    }

    private static boolean containsOnly(Collection<?> values, Long expected) {
        return values != null && values.size() == 1 && values.contains(expected);
    }
}
