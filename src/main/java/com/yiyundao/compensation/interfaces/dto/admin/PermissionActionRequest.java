package com.yiyundao.compensation.interfaces.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class PermissionActionRequest {

    @NotBlank(message = "操作编码不能为空")
    @Size(max = 100, message = "操作编码不能超过100个字符")
    private String code;

    @NotBlank(message = "操作名称不能为空")
    @Size(max = 100, message = "操作名称不能超过100个字符")
    private String name;

    @Size(max = 255, message = "操作描述不能超过255个字符")
    private String description;

    @Size(max = 100, message = "HTTP方法配置不能超过100个字符")
    private String httpMethods;

    @Size(max = 150, message = "authority不能超过150个字符")
    private String authority;

    private String status;
    private Integer orderNum;
    private Map<String, Object> props;
}
