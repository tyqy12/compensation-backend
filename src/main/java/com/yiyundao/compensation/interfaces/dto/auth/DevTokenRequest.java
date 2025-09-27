package com.yiyundao.compensation.interfaces.dto.auth;

import lombok.Data;

import java.util.List;

@Data
public class DevTokenRequest {
    private String username;
    private List<String> roles;       // e.g. ["ADMIN","MANAGER"]
    private List<String> authorities; // e.g. ["approval:start","approval:read"]
}

