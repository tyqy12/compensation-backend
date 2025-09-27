package com.yiyundao.compensation.interfaces.controller.auth;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.interfaces.dto.auth.DevTokenRequest;
import com.yiyundao.compensation.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Profile("dev")
public class DevTokenController {

    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/dev-token")
    public ApiResponse<Map<String, String>> generateDevToken(@RequestBody DevTokenRequest req) {
        List<GrantedAuthority> auths = new ArrayList<>();
        if (req.getRoles() != null) {
            for (String r : req.getRoles()) {
                if (r != null && !r.isBlank()) {
                    auths.add(new SimpleGrantedAuthority("ROLE_" + r.trim()));
                }
            }
        }
        if (req.getAuthorities() != null) {
            for (String a : req.getAuthorities()) {
                if (a != null && !a.isBlank()) {
                    auths.add(new SimpleGrantedAuthority(a.trim()));
                }
            }
        }
        String username = (req.getUsername() == null || req.getUsername().isBlank()) ? "dev" : req.getUsername();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(username, null, auths);
        String token = jwtTokenProvider.generateToken(authentication);
        Map<String, String> body = new HashMap<>();
        body.put("token", token);
        return ApiResponse.success(body);
    }
}

