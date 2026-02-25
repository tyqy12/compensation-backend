package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollPreviewDto;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PayrollReportServiceImplTest {

    @Mock
    private PayrollLineService payrollLineService;
    @Mock
    private PayrollBatchService payrollBatchService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PayrollReportServiceImpl reportService;

    private PayrollBatch batch;

    @BeforeEach
    void setUp() {
        batch = new PayrollBatch();
        batch.setId(55L);
        batch.setPeriodLabel("2024-08");
        batch.setCurrency("CNY");
    }

    @Test
    void basicReportAggregatesDepartmentTotals() throws Exception {
        PayrollLine line1 = new PayrollLine();
        line1.setId(1L);
        line1.setBatchId(55L);
        line1.setEmployeeId(101L);
        line1.setGrossAmount(new BigDecimal("10000"));
        line1.setTaxAmount(new BigDecimal("1000"));
        line1.setSocialAmount(new BigDecimal("500"));
        line1.setNetAmount(new BigDecimal("8500"));
        line1.setItemsSnapshotJson("[{\"type\":\"earning\",\"amount\":8000},{\"type\":\"deduction\",\"amount\":500}]\n");

        PayrollLine line2 = new PayrollLine();
        line2.setId(2L);
        line2.setBatchId(55L);
        line2.setEmployeeId(102L);
        line2.setGrossAmount(new BigDecimal("8000"));
        line2.setTaxAmount(new BigDecimal("800"));
        line2.setSocialAmount(new BigDecimal("400"));
        line2.setNetAmount(new BigDecimal("6800"));
        line2.setItemsSnapshotJson("[{\"type\":\"earning\",\"amount\":6000},{\"type\":\"deduction\",\"amount\":200}]\n");

        Employee emp1 = new Employee();
        emp1.setId(101L);
        emp1.setDepartment("Finance");

        Employee emp2 = new Employee();
        emp2.setId(102L);
        emp2.setDepartment("HR");

        Mockito.when(payrollLineService.list(ArgumentMatchers.<LambdaQueryWrapper<PayrollLine>>any()))
                .thenReturn(List.of(line1, line2));
        Mockito.when(payrollBatchService.listByIds(ArgumentMatchers.anyCollection())).thenReturn(List.of(batch));
        Mockito.when(employeeService.listByIds(ArgumentMatchers.anyCollection())).thenReturn(List.of(emp1, emp2));

        List<PayrollPreviewDto.PayrollPreviewItemDto> items1 = List.of(createItem("earning", "8000"), createItem("deduction", "500"));
        List<PayrollPreviewDto.PayrollPreviewItemDto> items2 = List.of(createItem("earning", "6000"), createItem("deduction", "200"));

        Mockito.when(objectMapper.readValue(Mockito.eq(line1.getItemsSnapshotJson()), ArgumentMatchers.<TypeReference<List<PayrollPreviewDto.PayrollPreviewItemDto>>>any()))
                .thenReturn(items1);
        Mockito.when(objectMapper.readValue(Mockito.eq(line2.getItemsSnapshotJson()), ArgumentMatchers.<TypeReference<List<PayrollPreviewDto.PayrollPreviewItemDto>>>any()))
                .thenReturn(items2);

        var report = reportService.basicReport(55L, null, null);

        assertThat(report.getBatchId()).isEqualTo(55L);
        assertThat(report.getDepartments()).hasSize(2);
        assertThat(report.getGrossTotal()).isEqualByComparingTo("18000");
        assertThat(report.getEarningsTotal()).isEqualByComparingTo("14000");
        assertThat(report.getDeductionsTotal()).isEqualByComparingTo("700");
        assertThat(report.getEmployeeCount()).isEqualTo(2);
    }

    private PayrollPreviewDto.PayrollPreviewItemDto createItem(String type, String amount) {
        PayrollPreviewDto.PayrollPreviewItemDto dto = new PayrollPreviewDto.PayrollPreviewItemDto();
        dto.setType(type);
        dto.setAmount(new BigDecimal(amount));
        return dto;
    }
}
