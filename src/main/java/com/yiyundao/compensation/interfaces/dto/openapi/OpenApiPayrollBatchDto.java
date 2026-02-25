package com.yiyundao.compensation.interfaces.dto.openapi;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OpenApiPayrollBatchDto {
    private Long id;
    private String periodLabel;
    private String type;
    private String status;
    private String currency;
    private Long lineCount;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

