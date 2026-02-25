package com.yiyundao.compensation.interfaces.dto.openapi;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OpenApiPayrollLineDto {
    private Long id;
    private Long batchId;
    private String employeeRef;
    private String employmentType;
    private BigDecimal grossAmount;
    private BigDecimal taxAmount;
    private BigDecimal socialAmount;
    private BigDecimal netAmount;
    private String currency;
    private List<String> departments;
    private String employeeNameMasked;
    private String phoneMasked;
    private LocalDateTime generatedAt;
}

