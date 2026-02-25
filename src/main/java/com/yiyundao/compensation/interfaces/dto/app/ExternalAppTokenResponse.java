package com.yiyundao.compensation.interfaces.dto.app;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExternalAppTokenResponse {
    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private String scope;
}

