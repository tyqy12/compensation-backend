package com.yiyundao.compensation.interfaces.dto.employee;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    @NotBlank
    private String status; // active/inactive/terminated 等
}

