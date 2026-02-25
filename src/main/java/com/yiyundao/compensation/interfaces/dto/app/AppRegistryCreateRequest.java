package com.yiyundao.compensation.interfaces.dto.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AppRegistryCreateRequest {

    @NotBlank
    private String appName;

    @NotEmpty
    private List<String> scopes;

    private List<String> ipWhitelist;

    private String webhookUrl;

    private String status;
}

