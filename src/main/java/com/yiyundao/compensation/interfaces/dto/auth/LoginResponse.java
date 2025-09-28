package com.yiyundao.compensation.interfaces.dto.auth;

import lombok.Data;

import java.util.List;

@Data
public class LoginResponse {
    private String token;
    private String refreshToken;
    private String username;
    private List<String> roles;
}

