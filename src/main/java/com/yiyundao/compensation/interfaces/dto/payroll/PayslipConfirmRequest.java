package com.yiyundao.compensation.interfaces.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PayslipConfirmRequest {

    @NotBlank(message = "签字内容不能为空")
    private String signature;

    private String comment;
}
