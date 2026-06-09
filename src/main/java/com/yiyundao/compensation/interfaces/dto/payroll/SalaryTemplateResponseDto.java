package com.yiyundao.compensation.interfaces.dto.payroll;

import com.yiyundao.compensation.modules.payroll.entity.SalaryTemplate;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SalaryTemplateResponseDto {

    private Long id;
    private String name;
    private String type;
    private String itemsJson;
    private String taxRuleJson;
    private String status;
    private Long dataVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static SalaryTemplateResponseDto from(SalaryTemplate template) {
        if (template == null) {
            return null;
        }
        return SalaryTemplateResponseDto.builder()
                .id(template.getId())
                .name(template.getName())
                .type(template.getType())
                .itemsJson(template.getItemsJson())
                .taxRuleJson(template.getTaxRuleJson())
                .status(template.getStatus())
                .dataVersion(template.getDataVersion())
                .createTime(template.getCreateTime())
                .updateTime(template.getUpdateTime())
                .build();
    }
}
