package com.yiyundao.compensation.interfaces.controller.payroll;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.payroll.SalaryTemplateUpsertRequest;
import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplate;
import com.yiyundao.compensation.modules.payroll.service.SalaryTemplateService;
import com.yiyundao.compensation.modules.payroll.service.SalaryTemplateVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalaryTemplateControllerTest {

    private SalaryTemplateService salaryTemplateService;
    private SalaryTemplateVersionService salaryTemplateVersionService;
    private SalaryTemplateController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        salaryTemplateService = mock(SalaryTemplateService.class);
        salaryTemplateVersionService = mock(SalaryTemplateVersionService.class);
        when(salaryTemplateVersionService.save(any())).thenReturn(true);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        controller = new SalaryTemplateController(salaryTemplateService, salaryTemplateVersionService, objectMapper);
    }

    @Test
    void createShouldReturnPublicResponseWithoutPersistenceFields() throws Exception {
        when(salaryTemplateService.save(any(SalaryTemplate.class))).thenAnswer(invocation -> {
            SalaryTemplate template = invocation.getArgument(0);
            enrichPersistenceFields(template);
            return true;
        });

        String json = objectMapper.writeValueAsString(controller.create(upsertRequest()));

        assertPublicResponseShape(json);
        assertThat(json)
                .contains("\"name\":\"Full-time standard\"")
                .contains("\"type\":\"full_time\"")
                .contains("\"dataVersion\":1");
    }

    @Test
    void listShouldReturnPublicPageRecordsWithoutPersistenceFields() throws Exception {
        Page<SalaryTemplate> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(template()));
        when(salaryTemplateService.page(any(Page.class), any())).thenReturn(page);

        String json = objectMapper.writeValueAsString(controller.list(1, 10, "full_time", "enabled"));

        assertPublicResponseShape(json);
        assertThat(json)
                .contains("\"records\"")
                .contains("\"total\":1")
                .contains("\"dataVersion\":3");
    }

    @Test
    void getShouldReturnPublicResponseWithoutPersistenceFields() throws Exception {
        when(salaryTemplateService.getById(10L)).thenReturn(template());

        String json = objectMapper.writeValueAsString(controller.get(10L));

        assertPublicResponseShape(json);
        assertThat(json)
                .contains("\"itemsJson\"")
                .contains("\"taxRuleJson\"");
    }

    @Test
    void listShouldRejectInvalidFilters() {
        assertThatThrownBy(() -> controller.list(1, 10, "unknown", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_INVALID);
        assertThatThrownBy(() -> controller.list(1, 10, null, "unknown"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_INVALID);

        verify(salaryTemplateService, never()).page(any(Page.class), any());
    }

    @Test
    void createShouldRejectInvalidTypeAndStatusBeforeSaving() {
        SalaryTemplateUpsertRequest badType = upsertRequest();
        badType.setType("contractor");

        assertThatThrownBy(() -> controller.create(badType))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_INVALID);

        SalaryTemplateUpsertRequest badStatus = upsertRequest();
        badStatus.setStatus("archived");

        assertThatThrownBy(() -> controller.create(badStatus))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_INVALID);

        verify(salaryTemplateService, never()).save(any(SalaryTemplate.class));
    }

    @Test
    void createShouldRejectDuplicateItemCodesBeforeSaving() {
        SalaryTemplateUpsertRequest request = upsertRequest();
        request.setItemsJson("""
                [
                  {"code":"base","name":"基本工资","type":"earning","required":true},
                  {"code":"base","name":"重复基本工资","type":"earning","required":false}
                ]
                """);

        var response = controller.create(request);

        assertThat(response.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
        assertThat(response.getMessage()).contains("code重复");
        verify(salaryTemplateService, never()).save(any(SalaryTemplate.class));
    }

    @Test
    void createShouldRejectInvalidItemBoundsBeforeSaving() {
        SalaryTemplateUpsertRequest request = upsertRequest();
        request.setItemsJson("""
                [
                  {"code":"base","name":"基本工资","type":"earning","required":true,"min":1000,"max":100},
                  {"code":"deduct","name":"扣款","type":"deduction","required":false,"min":-1}
                ]
                """);

        var response = controller.create(request);

        assertThat(response.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
        assertThat(response.getMessage()).contains("min不能大于max", "min不能小于0");
        verify(salaryTemplateService, never()).save(any(SalaryTemplate.class));
    }

    @Test
    void createShouldRejectOutOfRangeRatesBeforeSaving() {
        SalaryTemplateUpsertRequest request = upsertRequest();
        request.setTaxRuleJson("""
                {
                  "tax": {"rate": -0.01, "applyOn": "GROSS"},
                  "social": {"rate": 1.01, "applyOn": "GROSS"}
                }
                """);

        var response = controller.create(request);

        assertThat(response.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
        assertThat(response.getMessage()).contains("taxRuleJson.tax.rate必须在0到1之间",
                "taxRuleJson.social.rate必须在0到1之间");
        verify(salaryTemplateService, never()).save(any(SalaryTemplate.class));
    }

    @Test
    void publishShouldEnableAValidDisabledTemplate() {
        SalaryTemplate template = template();
        template.setStatus("disabled");
        when(salaryTemplateService.getById(10L)).thenReturn(template);
        when(salaryTemplateService.count(any())).thenReturn(0L);
        when(salaryTemplateService.updateById(any(SalaryTemplate.class))).thenReturn(true);

        var response = controller.publish(10L);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().getStatus()).isEqualTo("enabled");
        verify(salaryTemplateService).updateById(template);
    }

    @Test
    void publishShouldRejectAnotherEnabledTemplateForSameType() {
        SalaryTemplate template = template();
        template.setStatus("disabled");
        when(salaryTemplateService.getById(10L)).thenReturn(template);
        when(salaryTemplateService.count(any())).thenReturn(1L);

        var response = controller.publish(10L);

        assertThat(response.getCode()).isEqualTo(ErrorCode.REQUEST_CONFLICT.getCode());
        verify(salaryTemplateService, never()).updateById(any(SalaryTemplate.class));
    }

    @Test
    void updateShouldRejectInvalidTypeAndStatusBeforeSaving() {
        when(salaryTemplateService.getById(10L)).thenReturn(template());
        SalaryTemplateUpsertRequest badType = upsertRequest();
        badType.setType("contractor");

        assertThatThrownBy(() -> controller.update(10L, badType))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_INVALID);

        SalaryTemplateUpsertRequest badStatus = upsertRequest();
        badStatus.setStatus("archived");

        assertThatThrownBy(() -> controller.update(10L, badStatus))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_INVALID);

        verify(salaryTemplateService, never()).updateById(any(SalaryTemplate.class));
    }

    @Test
    void listShouldClampPageAndSizeBeforeQuerying() {
        Page<SalaryTemplate> page = new Page<>(1, 200, 0);
        page.setRecords(List.of());
        when(salaryTemplateService.page(any(Page.class), any())).thenReturn(page);

        controller.list(-1, 1000, null, null);

        ArgumentCaptor<Page<SalaryTemplate>> captor = ArgumentCaptor.forClass(Page.class);
        verify(salaryTemplateService).page(captor.capture(), any());
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(200);
    }

    private static SalaryTemplateUpsertRequest upsertRequest() {
        SalaryTemplateUpsertRequest request = new SalaryTemplateUpsertRequest();
        request.setName("Full-time standard");
        request.setType("full_time");
        request.setItemsJson("[{\"code\":\"base\",\"name\":\"基本工资\",\"type\":\"earning\",\"required\":true}]");
        request.setTaxRuleJson("{\"tax\":{\"rate\":0.1,\"applyOn\":\"GROSS\"}}");
        request.setStatus("enabled");
        return request;
    }

    private static SalaryTemplate template() {
        SalaryTemplate template = new SalaryTemplate();
        template.setName("Full-time standard");
        template.setType("full_time");
        template.setItemsJson("[{\"code\":\"base\",\"name\":\"基本工资\",\"type\":\"earning\",\"required\":true}]");
        template.setTaxRuleJson("{\"tax\":{\"rate\":0.1,\"applyOn\":\"GROSS\"}}");
        template.setStatus("enabled");
        template.setDataVersion(3L);
        enrichPersistenceFields(template);
        return template;
    }

    private static void enrichPersistenceFields(SalaryTemplate template) {
        template.setId(10L);
        template.setCreateTime(LocalDateTime.of(2026, 6, 2, 12, 0));
        template.setUpdateTime(LocalDateTime.of(2026, 6, 2, 12, 1));
        template.setCreateBy("finance");
        template.setUpdateBy("finance");
        template.setDeleted(0);
        template.setVersion(9);
    }

    private static void assertPublicResponseShape(String json) {
        assertThat(json)
                .contains("\"id\":10")
                .contains("\"status\":\"enabled\"")
                .contains("\"createTime\"")
                .contains("\"updateTime\"")
                .doesNotContain("deleted")
                .doesNotContain("version")
                .doesNotContain("createBy")
                .doesNotContain("updateBy");
    }
}
