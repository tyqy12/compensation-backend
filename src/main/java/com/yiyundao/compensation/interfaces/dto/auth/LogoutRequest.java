package com.yiyundao.compensation.interfaces.dto.auth;

import lombok.Data;

@Data
public class LogoutRequest {
    private String refreshToken; // 可选
}

