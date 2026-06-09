package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
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
import java.time.LocalDateTime;
import java.util.Collection;
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

    @Test
    void basicReportByPeriodShouldUseLatestReportableBatchPerPayrollType() throws Exception {
        PayrollBatch rejected = batch(10L, "full_time", PayrollBatchStatus.REJECTED, "2024-08-01T10:00:00");
        PayrollBatch oldConfirmed = batch(11L, "full_time", PayrollBatchStatus.CONFIRMED, "2024-08-02T10:00:00");
        PayrollBatch latestApproved = batch(12L, "full_time", PayrollBatchStatus.APPROVED, "2024-08-03T10:00:00");
        PayrollBatch partTimeApproved = batch(13L, "part_time", PayrollBatchStatus.APPROVED, "2024-08-02T11:00:00");

        PayrollLine latestLine = reportLine(12L, 201L, "3000");
        PayrollLine partTimeLine = reportLine(13L, 202L, "500");

        Employee emp1 = new Employee();
        emp1.setId(201L);
        emp1.setDepartment("Finance");
        Employee emp2 = new Employee();
        emp2.setId(202L);
        emp2.setDepartment("Finance");

        Mockito.when(payrollBatchService.list(ArgumentMatchers.<LambdaQueryWrapper<PayrollBatch>>any()))
                .thenReturn(List.of(rejected, oldConfirmed, latestApproved, partTimeApproved));
        Mockito.when(payrollLineService.list(ArgumentMatchers.<LambdaQueryWrapper<PayrollLine>>any()))
                .thenReturn(List.of(latestLine, partTimeLine));
        Mockito.when(payrollBatchService.listByIds(ArgumentMatchers.anyCollection()))
                .thenAnswer(invocation -> {
                    Collection<Long> ids = invocation.getArgument(0);
                    assertThat(ids).containsExactly(12L, 13L);
                    return List.of(latestApproved, partTimeApproved);
                });
        Mockito.when(employeeService.listByIds(ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(emp1, emp2));
        Mockito.when(objectMapper.readValue(Mockito.anyString(),
                        ArgumentMatchers.<TypeReference<List<PayrollPreviewDto.PayrollPreviewItemDto>>>any()))
                .thenAnswer(invocation -> List.of(createItem("earning", invocation.getArgument(0, String.class).contains("500") ? "500" : "3000")));

        var report = reportService.basicReport(null, "2024-08", null);

        assertThat(report.getGrossTotal()).isEqualByComparingTo("3500");
        assertThat(report.getEmployeeCount()).isEqualTo(2);
        assertThat(report.getBatchId()).isEqualTo(12L);
    }

    @Test
    void exportBasicReportShouldEscapeCsvCellsAndProtectFormulas() throws Exception {
        PayrollLine formulaLine = new PayrollLine();
        formulaLine.setId(3L);
        formulaLine.setBatchId(55L);
        formulaLine.setEmployeeId(103L);
        formulaLine.setGrossAmount(new BigDecimal("1000"));
        formulaLine.setTaxAmount(BigDecimal.ZERO);
        formulaLine.setSocialAmount(BigDecimal.ZERO);
        formulaLine.setNetAmount(new BigDecimal("1000"));
        formulaLine.setItemsSnapshotJson("[]");

        PayrollLine commaLine = new PayrollLine();
        commaLine.setId(4L);
        commaLine.setBatchId(55L);
        commaLine.setEmployeeId(104L);
        commaLine.setGrossAmount(new BigDecimal("2000"));
        commaLine.setTaxAmount(BigDecimal.ZERO);
        commaLine.setSocialAmount(BigDecimal.ZERO);
        commaLine.setNetAmount(new BigDecimal("2000"));
        commaLine.setItemsSnapshotJson("[]");

        Employee formulaDept = new Employee();
        formulaDept.setId(103L);
        formulaDept.setDepartment("=HYPERLINK(\"http://evil\")");

        Employee commaDept = new Employee();
        commaDept.setId(104L);
        commaDept.setDepartment("Finance, North");

        Mockito.when(payrollLineService.list(ArgumentMatchers.<LambdaQueryWrapper<PayrollLine>>any()))
                .thenReturn(List.of(formulaLine, commaLine));
        Mockito.when(payrollBatchService.listByIds(ArgumentMatchers.anyCollection())).thenReturn(List.of(batch));
        Mockito.when(employeeService.listByIds(ArgumentMatchers.anyCollection())).thenReturn(List.of(formulaDept, commaDept));
        Mockito.when(objectMapper.readValue(Mockito.eq("[]"),
                        ArgumentMatchers.<TypeReference<List<PayrollPreviewDto.PayrollPreviewItemDto>>>any()))
                .thenReturn(List.of());

        String csv = new String(reportService.exportBasicReport(55L, null, null));

        assertThat(csv).contains("'=HYPERLINK");
        assertThat(csv).doesNotContain("\n=HYPERLINK");
        assertThat(csv).contains("\"Finance, North\"");
    }

    private PayrollPreviewDto.PayrollPreviewItemDto createItem(String type, String amount) {
        PayrollPreviewDto.PayrollPreviewItemDto dto = new PayrollPreviewDto.PayrollPreviewItemDto();
        dto.setType(type);
        dto.setAmount(new BigDecimal(amount));
        return dto;
    }

    private PayrollBatch batch(Long id, String type, PayrollBatchStatus status, String createTime) {
        PayrollBatch payrollBatch = new PayrollBatch();
        payrollBatch.setId(id);
        payrollBatch.setType(type);
        payrollBatch.setStatus(status);
        payrollBatch.setPeriodLabel("2024-08");
        payrollBatch.setCurrency("CNY");
        payrollBatch.setCreateTime(LocalDateTime.parse(createTime));
        return payrollBatch;
    }

    private PayrollLine reportLine(Long batchId, Long employeeId, String amount) {
        PayrollLine line = new PayrollLine();
        line.setBatchId(batchId);
        line.setEmployeeId(employeeId);
        line.setGrossAmount(new BigDecimal(amount));
        line.setTaxAmount(BigDecimal.ZERO);
        line.setSocialAmount(BigDecimal.ZERO);
        line.setNetAmount(new BigDecimal(amount));
        line.setItemsSnapshotJson("[{\"type\":\"earning\",\"amount\":" + amount + "}]");
        return line;
    }
}
