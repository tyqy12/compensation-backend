package com.yiyundao.compensation.modules.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.modules.payment.dto.EmployeeTypeMappingDto;
import com.yiyundao.compensation.modules.payment.entity.EmployeeTypeProviderMapping;
import com.yiyundao.compensation.modules.payment.service.SettlementProviderRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettlementProviderRoutingControllerTest {

    private SettlementProviderRoutingService routingService;
    private SettlementProviderRoutingController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        routingService = mock(SettlementProviderRoutingService.class);
        controller = new SettlementProviderRoutingController(routingService);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void createMappingShouldReturnResponseWithoutPersistenceFields() throws Exception {
        EmployeeTypeMappingDto request = new EmployeeTypeMappingDto();
        request.setEmploymentType(EmploymentType.PART_TIME);
        request.setProviderCode("alipay");
        request.setPriority(20);
        when(routingService.createMapping(EmploymentType.PART_TIME, "alipay", 20)).thenReturn(mapping());

        ApiResponse<?> response = controller.createMapping(request);

        assertPublicResponseShape(response);
    }

    @Test
    void listMappingsShouldReturnResponseWithoutPersistenceFields() throws Exception {
        when(routingService.getAllMappings()).thenReturn(List.of(mapping()));

        ApiResponse<?> response = controller.getAllMappings();

        assertPublicResponseShape(response);
    }

    private void assertPublicResponseShape(Object response) throws Exception {
        String json = objectMapper.writeValueAsString(response);

        assertThat(json)
                .contains("\"providerCode\":\"alipay\"")
                .contains("\"employmentTypeCode\":\"part_time\"")
                .contains("\"employmentTypeName\":\"兼职\"")
                .contains("\"priority\":20")
                .contains("\"enabled\":true")
                .doesNotContain("deleted")
                .doesNotContain("version")
                .doesNotContain("createBy")
                .doesNotContain("updateBy");
    }

    private EmployeeTypeProviderMapping mapping() {
        EmployeeTypeProviderMapping mapping = new EmployeeTypeProviderMapping();
        mapping.setId(9L);
        mapping.setEmploymentType(EmploymentType.PART_TIME);
        mapping.setProviderCode("alipay");
        mapping.setPriority(20);
        mapping.setEnabled(true);
        mapping.setCreateTime(LocalDateTime.of(2026, 6, 2, 12, 0));
        mapping.setUpdateTime(LocalDateTime.of(2026, 6, 2, 12, 1));
        mapping.setCreateBy("system");
        mapping.setUpdateBy("system");
        mapping.setDeleted(0);
        mapping.setVersion(3);
        return mapping;
    }
}
