package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PayslipObjectionRequest {

    @NotBlank(message = "异议原因不能为空")
    private String reason;

    private String comment;
}
