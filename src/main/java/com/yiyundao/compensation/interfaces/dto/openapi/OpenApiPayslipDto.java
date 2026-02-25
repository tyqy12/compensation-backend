package com.yiyundao.compensation.interfaces.dto.openapi;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OpenApiPayslipDto {
    private Long id;
    private String employeeRef;
    private String period;
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
    private List<PayslipItemDto> items;

    @Data
    @Builder
    public static class PayslipItemDto {
        private String code;
        private String name;
        private String type;
        private Boolean taxable;
        private BigDecimal amount;
        private Boolean showOnPayslip;
        private Integer order;
    }
}

