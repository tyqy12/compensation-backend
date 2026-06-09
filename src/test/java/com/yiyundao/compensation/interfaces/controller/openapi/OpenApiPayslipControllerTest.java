package com.yiyundao.compensation.interfaces.controller.openapi;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.openapi.OpenApiPayrollBatchDto;
import com.yiyundao.compensation.interfaces.dto.openapi.OpenApiPayrollLineDto;
import com.yiyundao.compensation.interfaces.dto.openapi.OpenApiPayslipDto;
import com.yiyundao.compensation.modules.payroll.service.ExternalPayrollQueryService;
import com.yiyundao.compensation.security.ExternalApiContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiPayslipControllerTest {

    private ExternalApiContext externalApiContext;
    private TestExternalPayrollQueryService queryService;
    private OpenApiPayslipController controller;

    @BeforeEach
    void setUp() {
        externalApiContext = new ExternalApiContext();
        queryService = new TestExternalPayrollQueryService();
        controller = new OpenApiPayslipController(queryService, externalApiContext);
    }

    @AfterEach
    void tearDown() {
        externalApiContext.clear();
    }

    @Test
    void queryPayslipsReturnsErrorWhenPeriodInvalid() {
        externalApiContext.set(ExternalApiContext.ExternalApiClient.builder()
                .scopes(List.of("payslip:read"))
                .build());

        ApiResponse<List<OpenApiPayslipDto>> response = controller.queryPayslips("emp:E01", "invalid");

        assertThat(response.getCode()).isEqualTo(ErrorCode.PARAM_FORMAT_ERROR.getCode());
        assertThat(response.getMessage()).contains("period");
    }

    @Test
    void queryPayslipsReturnsDataWhenScopePresent() {
        externalApiContext.set(ExternalApiContext.ExternalApiClient.builder()
                .scopes(List.of("payslip:read"))
                .build());

        ApiResponse<List<OpenApiPayslipDto>> response = controller.queryPayslips("emp:E01", "2025-09");

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).hasSize(1);
        OpenApiPayslipDto dto = response.getData().get(0);
        assertThat(dto.getEmployeeRef()).isEqualTo("emp:E01");
        assertThat(dto.getNetAmount()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void getPayslipReturnsErrorWhenEmployeeRefMissing() {
        externalApiContext.set(ExternalApiContext.ExternalApiClient.builder()
                .scopes(List.of("payslip:read"))
                .build());

        ApiResponse<OpenApiPayslipDto> response = controller.getPayslip(1L, " ");

        assertThat(response.getCode()).isEqualTo(ErrorCode.PARAM_MISSING.getCode());
        assertThat(response.getMessage()).contains("employeeRef");
    }

    private static class TestExternalPayrollQueryService implements ExternalPayrollQueryService {

        @Override
        public Page<OpenApiPayrollBatchDto> pagePtBatches(String period, String status, long page, long size) {
            return new Page<>();
        }

        @Override
        public OpenApiPayrollBatchDto findBatch(Long batchId) {
            return null;
        }

        @Override
        public Page<OpenApiPayrollLineDto> pageBatchLines(Long batchId, String employeeRef, long page, long size) {
            return new Page<>();
        }

        @Override
        public List<OpenApiPayslipDto> findPayslips(String employeeRef, String period) {
            return List.of(OpenApiPayslipDto.builder()
                    .id(1L)
                    .employeeRef(employeeRef)
                    .period(period)
                    .employmentType("part_time")
                    .grossAmount(BigDecimal.TEN)
                    .taxAmount(BigDecimal.ZERO)
                    .socialAmount(BigDecimal.ZERO)
                    .netAmount(BigDecimal.TEN)
                    .currency("CNY")
                    .generatedAt(LocalDateTime.now())
                    .items(List.of())
                    .build());
        }

        @Override
        public OpenApiPayslipDto findPayslip(Long payslipId, String employeeRef) {
            return null;
        }
    }
}
