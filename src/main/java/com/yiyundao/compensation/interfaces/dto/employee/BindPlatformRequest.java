package com.yiyundao.compensation.interfaces.dto.employee;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindPlatformRequest {
    @NotBlank
    private String platformUserId;
    @NotBlank
    private String platformType; // wechat/dingtalk/feishu
}

