package com.yiyundao.compensation.interfaces.dto.auth;

import lombok.Data;

@Data
public class OAuthAuthorizeResponse {
    private String url;
    private String state;
}

