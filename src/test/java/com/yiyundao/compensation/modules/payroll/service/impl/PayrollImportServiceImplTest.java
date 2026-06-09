package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollImportItemMapper;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollImportItem;
import com.yiyundao.compensation.modules.payroll.entity.SalaryItem;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import com.yiyundao.compensation.modules.payroll.service.SalaryItemService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollImportServiceImplTest {

    @Mock
    private PayrollBatchService payrollBatchService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private SalaryItemService salaryItemService;
    @Mock
    private PayrollLineService payrollLineService;
    @Mock
    private PayrollImportItemMapper importItemMapper;

    @Test
    void commitCsvShouldRecheckBatchStatusBeforeReplacingExistingImportItems() {
        PayrollImportServiceImpl service = new PayrollImportServiceImpl(
                payrollBatchService,
                employeeService,
                salaryItemService,
                payrollLineService,
                importItemMapper
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payroll.csv",
                "text/csv",
                "employeeId,itemCode,amount,note\nE001,BASIC,1000,ok\n".getBytes()
        );
        when(payrollBatchService.getById(12L)).thenReturn(batch(12L, PayrollBatchStatus.DRAFT));
        when(salaryItemService.list(org.mockito.ArgumentMatchers.<Wrapper<SalaryItem>>any()))
                .thenReturn(List.of(salaryItem("BASIC")));
        when(employeeService.getByEmployeeId("E001")).thenReturn(employee(101L, "E001"));
        when(payrollBatchService.getOne(any())).thenReturn(batch(12L, PayrollBatchStatus.SUBMITTED));

        assertThatThrownBy(() -> service.commitCsv(12L, file))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS);
                    assertThat(ex.getMessage()).contains("当前批次状态不允许继续录入");
                });

        verify(importItemMapper, never()).delete(any(Wrapper.class));
        verify(importItemMapper, never()).insert(any(PayrollImportItem.class));
    }

    @Test
    void commitCsvShouldRejectNonPositiveAndOverscaleAmountsWithoutReplacingExistingRows() throws Exception {
        PayrollImportServiceImpl service = new PayrollImportServiceImpl(
                payrollBatchService,
                employeeService,
                salaryItemService,
                payrollLineService,
                importItemMapper
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payroll.csv",
                "text/csv",
                ("employeeId,itemCode,amount,note\n"
                        + "E001,BASIC,0,zero\n"
                        + "E001,BASIC,-10,negative\n"
                        + "E001,BASIC,12345678901.00,too large\n"
                        + "E001,BASIC,10.123,too precise\n").getBytes()
        );
        when(payrollBatchService.getById(12L)).thenReturn(batch(12L, PayrollBatchStatus.DRAFT));
        when(salaryItemService.list(org.mockito.ArgumentMatchers.<Wrapper<SalaryItem>>any()))
                .thenReturn(List.of(salaryItem("BASIC")));
        when(employeeService.getByEmployeeId("E001")).thenReturn(employee(101L, "E001"));

        assertThatThrownBy(() -> service.commitCsv(12L, file))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(ex.getMessage()).contains("CSV导入失败", "存在 4 行无效数据");
                });

        String result = service.previewCsv(12L, file);
        JsonNode summary = new ObjectMapper().readTree(result);
        assertThat(summary.get("total").asInt()).isEqualTo(4);
        assertThat(summary.get("valid").asInt()).isZero();
        assertThat(summary.get("invalid").asInt()).isEqualTo(4);
        assertThat(summary.get("errors").toString()).contains("amount invalid");
        verify(importItemMapper, never()).delete(any(Wrapper.class));
        verify(importItemMapper, never()).insert(any(PayrollImportItem.class));
    }

    @Test
    void commitCsvShouldRejectPartialInvalidFileWithoutReplacingExistingRows() {
        PayrollImportServiceImpl service = new PayrollImportServiceImpl(
                payrollBatchService,
                employeeService,
                salaryItemService,
                payrollLineService,
                importItemMapper
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payroll.csv",
                "text/csv",
                ("employeeId,itemCode,amount,note\n"
                        + "E001,BASIC,1000,ok\n"
                        + "E999,BASIC,2000,missing employee\n").getBytes()
        );
        when(payrollBatchService.getById(12L)).thenReturn(batch(12L, PayrollBatchStatus.DRAFT));
        when(salaryItemService.list(org.mockito.ArgumentMatchers.<Wrapper<SalaryItem>>any()))
                .thenReturn(List.of(salaryItem("BASIC")));
        when(employeeService.getByEmployeeId("E001")).thenReturn(employee(101L, "E001"));
        when(employeeService.getByEmployeeId("E999")).thenReturn(null);

        assertThatThrownBy(() -> service.commitCsv(12L, file))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(ex.getMessage()).contains("CSV导入失败", "存在 1 行无效数据");
                });

        verify(importItemMapper, never()).delete(any(Wrapper.class));
        verify(importItemMapper, never()).insert(any(PayrollImportItem.class));
    }

    @Test
    void commitCsvShouldRejectEmptyDataFileWithoutReplacingExistingRows() {
        PayrollImportServiceImpl service = new PayrollImportServiceImpl(
                payrollBatchService,
                employeeService,
                salaryItemService,
                payrollLineService,
                importItemMapper
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payroll.csv",
                "text/csv",
                "employeeId,itemCode,amount,note\n".getBytes()
        );
        when(payrollBatchService.getById(12L)).thenReturn(batch(12L, PayrollBatchStatus.DRAFT));
        when(salaryItemService.list(org.mockito.ArgumentMatchers.<Wrapper<SalaryItem>>any()))
                .thenReturn(List.of(salaryItem("BASIC")));

        assertThatThrownBy(() -> service.commitCsv(12L, file))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(ex.getMessage()).contains("CSV导入失败", "CSV没有可导入的数据行");
                });

        verify(importItemMapper, never()).delete(any(Wrapper.class));
        verify(importItemMapper, never()).insert(any(PayrollImportItem.class));
    }

    @Test
    void commitCsvShouldThrowWhenFileCannotBeRead() {
        PayrollImportServiceImpl service = new PayrollImportServiceImpl(
                payrollBatchService,
                employeeService,
                salaryItemService,
                payrollLineService,
                importItemMapper
        );
        when(payrollBatchService.getById(12L)).thenReturn(batch(12L, PayrollBatchStatus.DRAFT));
        when(salaryItemService.list(org.mockito.ArgumentMatchers.<Wrapper<SalaryItem>>any()))
                .thenReturn(List.of(salaryItem("BASIC")));

        assertThatThrownBy(() -> service.commitCsv(12L, brokenMultipartFile("payroll.csv")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(ex.getMessage()).contains("CSV导入失败", "broken stream");
                });

        verify(importItemMapper, never()).delete(any(Wrapper.class));
        verify(importItemMapper, never()).insert(any(PayrollImportItem.class));
    }

    private PayrollBatch batch(Long id, PayrollBatchStatus status) {
        PayrollBatch batch = new PayrollBatch();
        batch.setId(id);
        batch.setStatus(status);
        return batch;
    }

    private Employee employee(Long id, String employeeNo) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeId(employeeNo);
        employee.setName("员工" + employeeNo);
        return employee;
    }

    private SalaryItem salaryItem(String code) {
        SalaryItem item = new SalaryItem();
        item.setId(1L);
        item.setCode(code);
        item.setName(code);
        item.setStatus("enabled");
        return item;
    }

    private MultipartFile brokenMultipartFile(String filename) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return filename;
            }

            @Override
            public String getContentType() {
                return "text/csv";
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long getSize() {
                return 1;
            }

            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("broken stream");
            }

            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("broken stream");
            }

            @Override
            public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                throw new IOException("broken stream");
            }
        };
    }
}
