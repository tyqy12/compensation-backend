package com.yiyundao.compensation.modules.payment.dto;

import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.modules.payment.entity.EmployeeTypeProviderMapping;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EmployeeTypeProviderMappingResponse {

    private Long id;
    private EmploymentType employmentType;
    private String employmentTypeCode;
    private String employmentTypeName;
    private String providerCode;
    private Integer priority;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static EmployeeTypeProviderMappingResponse from(EmployeeTypeProviderMapping mapping) {
        if (mapping == null) {
            return null;
        }
        EmploymentType employmentType = mapping.getEmploymentType();
        return EmployeeTypeProviderMappingResponse.builder()
                .id(mapping.getId())
                .employmentType(employmentType)
                .employmentTypeCode(employmentType == null ? null : employmentType.getCode())
                .employmentTypeName(employmentType == null ? null : employmentType.getName())
                .providerCode(mapping.getProviderCode())
                .priority(mapping.getPriority())
                .enabled(mapping.getEnabled())
                .createTime(mapping.getCreateTime())
                .updateTime(mapping.getUpdateTime())
                .build();
    }
}
