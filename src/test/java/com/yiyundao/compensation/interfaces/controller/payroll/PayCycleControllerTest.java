package com.yiyundao.compensation.interfaces.controller.payroll;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.payroll.PayCycleResponseDto;
import com.yiyundao.compensation.interfaces.dto.payroll.PayCycleUpsertRequest;
import com.yiyundao.compensation.modules.payroll.entity.PayCycle;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplate;
import com.yiyundao.compensation.modules.payroll.service.PayCycleService;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.SalaryTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayCycleControllerTest {

    @Mock
    private PayCycleService payCycleService;
    @Mock
    private PayrollBatchService payrollBatchService;
    @Mock
    private SalaryTemplateService salaryTemplateService;

    private PayCycleController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        controller = new PayCycleController(payCycleService, payrollBatchService, salaryTemplateService);
        SalaryTemplate enabledTemplate = enabledTemplate();
        lenient().when(salaryTemplateService.list(isA(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(enabledTemplate));
        lenient().when(salaryTemplateService.getById(enabledTemplate.getId())).thenReturn(enabledTemplate);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void createShouldReturnPublicResponseWithoutPersistenceFields() throws Exception {
        when(payCycleService.count(any())).thenReturn(0L);
        when(payCycleService.save(any(PayCycle.class))).thenAnswer(invocation -> {
            PayCycle cycle = invocation.getArgument(0);
            enrichPersistenceFields(cycle);
            return true;
        });

        String json = objectMapper.writeValueAsString(controller.create(upsertRequest("draft")));

        assertPublicResponseShape(json);
        assertThat(json)
                .contains("\"cycleCode\":\"CYCLE_FULL_TIME_2099-10\"")
                .contains("\"cycleName\":\"October payroll\"")
                .contains("\"status\":\"draft\"");
    }

    @Test
    void createShouldRejectOpeningAnIncompleteCalendar() {
        PayCycleUpsertRequest request = upsertRequest("open");
        request.setPayDay(null);

        assertThatThrownBy(() -> controller.create(request))
                .isInstanceOf(com.yiyundao.compensation.common.exception.BusinessException.class)
                .hasMessageContaining("有效发薪日");
        verify(payCycleService, never()).save(any(PayCycle.class));
    }

    @Test
    void listShouldReturnPublicPageRecordsWithoutPersistenceFields() throws Exception {
        Page<PayCycle> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(openCycle()));
        when(payCycleService.page(any(Page.class), any())).thenReturn(page);

        String json = objectMapper.writeValueAsString(controller.list(1, 10, "open", null, null));

        assertPublicResponseShape(json);
        assertThat(json)
                .contains("\"records\"")
                .contains("\"total\":1")
                .contains("\"status\":\"open\"");
    }

    @Test
    void getOpenCyclesShouldReturnPublicResponsesWithoutPersistenceFields() throws Exception {
        when(payCycleService.list(isA(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(openCycle()));

        String json = objectMapper.writeValueAsString(controller.getOpenCycles("full_time"));

        assertPublicResponseShape(json);
        assertThat(json)
                .contains("\"periodLabel\":\"2099-10\"")
                .contains("\"status\":\"open\"");
    }

    @Test
    void deleteShouldRejectDraftCycleWhenPayrollBatchLinked() {
        PayCycle cycle = draftCycle();
        when(payCycleService.getById(10L)).thenReturn(cycle);
        when(payrollBatchService.count(any())).thenReturn(1L);

        ApiResponse<Void> response = controller.delete(10L);

        assertThat(response.getCode()).isEqualTo(ErrorCode.REQUEST_CONFLICT.getCode());
        assertThat(response.getMessage()).contains("已关联薪资批次");
        verify(payCycleService, never()).removeById(10L);
    }

    @Test
    void deleteShouldRemoveDraftCycleWhenNoPayrollBatchLinked() {
        PayCycle cycle = draftCycle();
        when(payCycleService.getById(10L)).thenReturn(cycle);
        when(payrollBatchService.count(any())).thenReturn(0L);
        when(payCycleService.removeById(10L)).thenReturn(true);

        ApiResponse<Void> response = controller.delete(10L);

        assertThat(response.getCode()).isZero();
        verify(payCycleService).removeById(10L);
    }

    @Test
    void listShouldClampPageAndSizeBeforeQuerying() {
        Page<PayCycle> page = new Page<>(1, 200, 0);
        page.setRecords(List.of());
        when(payCycleService.page(any(Page.class), any())).thenReturn(page);

        controller.list(-1, 1000, null, null, null);

        ArgumentCaptor<Page<PayCycle>> captor = ArgumentCaptor.forClass(Page.class);
        verify(payCycleService).page(captor.capture(), any());
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(200);
    }

    @Test
    void updateShouldReturnConflictWhenStatusChangedBeforeConditionalUpdate() {
        PayCycle cycle = draftCycle();
        when(payCycleService.getById(10L)).thenReturn(cycle);
        when(payCycleService.count(any())).thenReturn(0L);
        when(payCycleService.update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(false);

        ApiResponse<PayCycleResponseDto> response = controller.update(10L, upsertRequest("open"));

        assertThat(response.getCode()).isEqualTo(ErrorCode.REQUEST_CONFLICT.getCode());
        assertThat(response.getMessage()).contains("状态已变更");
        verify(payCycleService, never()).updateById(any(PayCycle.class));
    }

    @Test
    void advanceStatusShouldUseConditionalUpdateAndReturnConflictWhenStatusChanged() {
        PayCycle cycle = draftCycle();
        when(payCycleService.getById(10L)).thenReturn(cycle);
        when(payCycleService.update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(false);

        ApiResponse<PayCycleResponseDto> response = controller.advanceStatus(10L);

        assertThat(response.getCode()).isEqualTo(ErrorCode.REQUEST_CONFLICT.getCode());
        assertThat(response.getMessage()).contains("状态已变更");
    }

    @Test
    void advanceStatusShouldReturnNextStatusWhenConditionalUpdateWins() {
        PayCycle cycle = draftCycle();
        when(payCycleService.getById(10L)).thenReturn(cycle);
        when(payCycleService.update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(true);

        ApiResponse<PayCycleResponseDto> response = controller.advanceStatus(10L);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getStatus()).isEqualTo("open");
    }

    private static PayCycle draftCycle() {
        PayCycle cycle = new PayCycle();
        cycle.setId(10L);
        cycle.setType("full_time");
        cycle.setPeriodLabel("2099-10");
        cycle.setCycleType("monthly");
        cycle.setStartDate(LocalDate.of(2099, 10, 1));
        cycle.setEndDate(LocalDate.of(2099, 10, 31));
        cycle.setCutoffDate(LocalDate.of(2099, 10, 25));
        cycle.setPayDay(30);
        cycle.setStatus("draft");
        return cycle;
    }

    private static PayCycle openCycle() {
        PayCycle cycle = draftCycle();
        cycle.setStatus("open");
        cycle.setCycleCode("CYCLE_FULL_TIME_2099-10");
        cycle.setCycleName("October payroll");
        cycle.setCycleType("monthly");
        cycle.setStartDate(LocalDate.of(2099, 10, 1));
        cycle.setEndDate(LocalDate.of(2099, 10, 31));
        cycle.setCutoffDate(LocalDate.of(2099, 10, 25));
        cycle.setPayDay(30);
        cycle.setLeadDays(3);
        cycle.setGraceDays(2);
        cycle.setTimezone("UTC+8");
        cycle.setDescription("public pay cycle");
        cycle.setNextExecutionTime(LocalDateTime.of(2099, 10, 30, 9, 0));
        cycle.setLastExecutionTime(LocalDateTime.of(2099, 9, 30, 9, 0));
        enrichPersistenceFields(cycle);
        return cycle;
    }

    private static PayCycleUpsertRequest upsertRequest(String status) {
        PayCycleUpsertRequest request = new PayCycleUpsertRequest();
        request.setType("full_time");
        request.setPeriodLabel("2099-10");
        request.setCycleName("October payroll");
        request.setCycleType("monthly");
        request.setStartDate(LocalDate.of(2099, 10, 1));
        request.setEndDate(LocalDate.of(2099, 10, 31));
        request.setCutoffDate(LocalDate.of(2099, 10, 25));
        request.setPayDay(30);
        request.setLeadDays(3);
        request.setGraceDays(2);
        request.setTimezone("UTC+8");
        request.setDescription("public pay cycle");
        request.setStatus(status);
        return request;
    }

    private static void enrichPersistenceFields(PayCycle cycle) {
        cycle.setId(10L);
        cycle.setCreateTime(LocalDateTime.of(2026, 6, 2, 12, 0));
        cycle.setUpdateTime(LocalDateTime.of(2026, 6, 2, 12, 1));
        cycle.setCreateBy("finance");
        cycle.setUpdateBy("finance");
        cycle.setDeleted(0);
        cycle.setVersion(9);
    }

    private static SalaryTemplate enabledTemplate() {
        SalaryTemplate template = new SalaryTemplate();
        template.setId(20L);
        template.setName("Full-time standard");
        template.setType("full_time");
        template.setStatus("enabled");
        template.setDataVersion(1L);
        template.setDeleted(0);
        return template;
    }

    private static void assertPublicResponseShape(String json) {
        assertThat(json)
                .contains("\"id\":10")
                .contains("\"type\":\"full_time\"")
                .contains("\"periodLabel\":\"2099-10\"")
                .contains("\"createTime\"")
                .contains("\"updateTime\"")
                .doesNotContain("deleted")
                .doesNotContain("version")
                .doesNotContain("createBy")
                .doesNotContain("updateBy");
    }
}
