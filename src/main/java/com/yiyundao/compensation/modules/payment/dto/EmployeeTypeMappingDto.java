package com.yiyundao.compensation.modules.payment.dto;

import com.yiyundao.compensation.enums.EmploymentType;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 员工类型映射 DTO
 */
@Data
public class EmployeeTypeMappingDto {

    @NotNull(message = "员工类型不能为空")
    private EmploymentType employmentType;

    @NotBlank(message = "渠道代码不能为空")
    private String providerCode;

    private Integer priority;

    private Boolean enabled;
}
