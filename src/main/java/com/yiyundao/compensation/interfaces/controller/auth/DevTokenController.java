package com.yiyundao.compensation.interfaces.controller.auth;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.auth.DevTokenRequest;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
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
    private final SysUserService sysUserService;
    private final UserRoleService userRoleService;

    @PostMapping("/dev-token")
    public ApiResponse<Map<String, String>> generateDevToken(@RequestBody DevTokenRequest req) {
        // 默认使用 admin 用户（数据库中存在的用户）
        String username = (req.getUsername() == null || req.getUsername().isBlank()) ? "admin" : req.getUsername();

        // 从数据库获取用户信息
        SysUser user = sysUserService.findByUsername(username);
        if (user == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在: " + username);
        }

        List<GrantedAuthority> auths = new ArrayList<>();
        if (req.getRoles() != null && !req.getRoles().isEmpty()) {
            // 如果指定了 roles，使用指定的 roles
            for (String r : req.getRoles()) {
                if (r != null && !r.isBlank()) {
                    auths.add(new SimpleGrantedAuthority("ROLE_" + r.trim().toUpperCase()));
                }
            }
        } else {
            // 否则使用数据库中的 roles（从关联表查询）
            java.util.Set<String> userRoleCodes = userRoleService.getUserRoleCodes(user.getId());
            for (String r : userRoleCodes) {
                if (r != null && !r.isBlank()) {
                    String role = r.trim();
                    if (!role.startsWith("ROLE_")) role = "ROLE_" + role;
                    auths.add(new SimpleGrantedAuthority(role));
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

        String token = jwtTokenProvider.generateToken(username);
        Map<String, String> body = new HashMap<>();
        body.put("token", token);
        return ApiResponse.success(body);
    }
}
