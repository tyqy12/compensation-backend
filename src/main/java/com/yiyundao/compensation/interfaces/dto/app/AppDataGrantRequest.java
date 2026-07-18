package com.yiyundao.compensation.interfaces.dto.app;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppDataGrantRequest {

    @NotBlank
    private String scopeType;

    @NotBlank
    private String scopeValue;
}
