package com.yiyundao.compensation.interfaces.controller.auth;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.auth.DevTokenRequest;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Profile("(dev | development) & !prod & !production & !staging")
public class DevTokenController {

    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserService sysUserService;
    private final Environment environment;

    @PostMapping("/dev-token")
    public ApiResponse<Map<String, String>> generateDevToken(@RequestBody DevTokenRequest req) {
        if (!isDevOnlyProfile()) {
            return ApiResponse.error(ErrorCode.FORBIDDEN, "开发令牌接口不可用");
        }
        if (req == null || req.getUsername() == null || req.getUsername().isBlank()) {
            return ApiResponse.error(ErrorCode.PARAM_MISSING, "用户名不能为空");
        }

        String username = req.getUsername().trim();

        SysUser user = sysUserService.findByUsername(username);
        if (user == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在: " + username);
        }

        String token = jwtTokenProvider.generateToken(username);
        Map<String, String> body = new HashMap<>();
        body.put("token", token);
        return ApiResponse.success(body);
    }

    private boolean isDevOnlyProfile() {
        boolean hasDevProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile) || "development".equalsIgnoreCase(profile));
        boolean hasProdLikeProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile)
                        || "staging".equalsIgnoreCase(profile));
        return hasDevProfile && !hasProdLikeProfile;
    }
}
