package com.yiyundao.compensation.interfaces.dto.employee;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindPlatformRequest {

    @NotBlank
    @JsonAlias({"platformUserId"})
    private String subjectId;

    @NotBlank
    @JsonAlias({"platformType"})
    private String provider; // wechat/dingtalk/feishu
}
