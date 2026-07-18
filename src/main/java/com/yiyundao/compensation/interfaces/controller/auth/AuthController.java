package com.yiyundao.compensation.interfaces.controller.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.utils.SecretLogSanitizer;
import com.yiyundao.compensation.enums.UserStatus;
import com.yiyundao.compensation.interfaces.dto.auth.LoginRequest;
import com.yiyundao.compensation.interfaces.dto.auth.LoginResponse;
import com.yiyundao.compensation.interfaces.dto.auth.OAuthAuthorizeResponse;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.ClientIpResolver;
import com.yiyundao.compensation.security.JwtTokenProvider;
import com.yiyundao.compensation.service.PlatformOAuthService;
import com.yiyundao.compensation.service.WeComAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;
    private final UserRoleService userRoleService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PlatformOAuthService platformOAuthService;
    private final WeComAuthService weComAuthService;
    private final AuditLogService auditLogService;
    private final ExternalIdentityService externalIdentityService;
    private final com.yiyundao.compensation.service.AuthTokenService authTokenService;
    private final com.yiyundao.compensation.service.LoginRateLimiterService loginRateLimiterService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        String username = req == null ? null : req.getUsername();
        String password = req == null ? null : req.getPassword();
        try {
            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                audit("用户登录", username, request, false, "missing credentials", begin);
                return ApiResponse.error(ErrorCode.PARAM_MISSING, "用户名或密码为空");
            }
            String ip = clientIpResolver.resolve(request);
            if (loginRateLimiterService.isLocked(username, ip)) {
                audit("用户登录", username, request, false, "rate-limited", begin);
                return ApiResponse.error(ErrorCode.TOO_MANY_REQUESTS, "尝试过于频繁，请稍后再试");
            }
            SysUser user = sysUserService.getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
            if (user == null || !StringUtils.hasText(user.getPassword()) || !passwordEncoder.matches(password, user.getPassword())) {
                loginRateLimiterService.onFail(username, ip);
                audit("用户登录", username, request, false, "bad credentials", begin);
                return ApiResponse.error(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
            }
            if (!isActiveUser(user)) {
                loginRateLimiterService.onFail(username, ip);
                audit("用户登录", username, request, false, "inactive user", begin);
                return ApiResponse.error(ErrorCode.FORBIDDEN, "账号已禁用，请联系管理员");
            }
            List<GrantedAuthority> authorities = getAuthoritiesFromUser(user);
            // Token 仅包含用户身份，权限从数据库动态获取
            String token = jwtTokenProvider.generateToken(user.getUsername());
            String refresh = jwtTokenProvider.generateRefreshToken(user.getUsername());
            LoginResponse resp = new LoginResponse();
            resp.setToken(token);
            resp.setRefreshToken(refresh);
            resp.setUsername(user.getUsername());
            resp.setRoles(authorities.stream().map(GrantedAuthority::getAuthority).filter(a->a.startsWith("ROLE_")).collect(Collectors.toList()));
            // 存储刷新令牌（绑定用户）
            java.util.Date exp = jwtTokenProvider.getExpiration(refresh);
            if (exp != null) {
                long ttl = exp.getTime() - System.currentTimeMillis();
                if (!authTokenService.storeRefreshToken(user.getUsername(), refresh, ttl)) {
                    audit("用户登录", user.getUsername(), request, false, "refresh token persistence failed", begin);
                    return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "登录失败，请稍后重试");
                }
            } else {
                audit("用户登录", user.getUsername(), request, false, "refresh token expiration missing", begin);
                return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "登录失败，请稍后重试");
            }
            loginRateLimiterService.onSuccess(user.getUsername(), ip);
            audit("用户登录", user.getUsername(), request, true, null, begin);
            return ApiResponse.success("登录成功", resp);
        } catch (Exception e) {
            log.error("password login error for user: {}, errorType={}, error={}",
                    username, e.getClass().getSimpleName(), SecretLogSanitizer.sanitize(e));
            audit("用户登录", username, request, false, SecretLogSanitizer.sanitize(e), begin);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "登录失败，请稍后重试");
        }
    }

    @GetMapping("/oauth/authorize")
    public ApiResponse<OAuthAuthorizeResponse> authorize(@RequestParam String platform,
                                                         @RequestParam(required = false, defaultValue = "web") String channel,
                                                         @RequestParam String redirectUri) {
        String normalized = normalizeOAuthPlatform(platform);
        if (normalized == null) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "不支持的OAuth平台，支持：wechat|dingtalk|feishu");
        }
        PlatformOAuthService.Authorize a = platformOAuthService.buildAuthorize(normalized, redirectUri, channel);
        OAuthAuthorizeResponse out = new OAuthAuthorizeResponse();
        out.setUrl(a.getUrl());
        out.setState(a.getState());
        out.setChannel(a.getChannel());
        return ApiResponse.success(out);
    }

    @GetMapping("/oauth/callback/{platform}")
    public ApiResponse<LoginResponse> oauthCallback(@PathVariable String platform,
                                                    @RequestParam String code,
                                                    @RequestParam(required = false) String state,
                                                    HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        try {
            String normalized = normalizeOAuthPlatform(platform);
            if (normalized == null) {
                return ApiResponse.error(ErrorCode.PARAM_INVALID, "不支持的OAuth平台，支持：wechat|dingtalk|feishu");
            }
            // 校验并消费state，失败拒绝
            if (!StringUtils.hasText(state) || !authTokenService.consumeOAuthState(normalized, state)) {
                audit("OAuth登录-" + platform, null, request, false, "invalid state", begin);
                return ApiResponse.error(ErrorCode.PARAM_INVALID, "非法请求(state)");
            }
	            PlatformOAuthService.PlatformUser puser = platformOAuthService.exchangeCode(normalized, code);
	            if (puser == null || !StringUtils.hasText(puser.getSubjectId())) {
	                audit(normalized + "登录", null, request, false, "exchange failed", begin);
	                return ApiResponse.error(ErrorCode.UNAUTHORIZED, "第三方登录失败");
	            }
            // 找绑定用户：统一从 external_identity 查询
            String tenantKey = StringUtils.hasText(puser.getTenantKey())
                    ? puser.getTenantKey()
                    : ExternalIdentityService.DEFAULT_TENANT_KEY;
            String subjectType = StringUtils.hasText(puser.getSubjectType())
                    ? puser.getSubjectType()
                    : ExternalIdentityService.DEFAULT_SUBJECT_TYPE;
            Long userId = externalIdentityService.findBoundUserId(
                    normalized,
                    tenantKey,
                    subjectType,
                    puser.getSubjectId()
            );
            SysUser user = userId != null ? sysUserService.getById(userId) : null;
	            if (user == null) {
	                audit(normalized + "登录", null, request, false, "not bound", begin);
	                return ApiResponse.error(ErrorCode.FORBIDDEN, "未绑定该第三方账号，请联系管理员绑定后再登录");
	            }
            if (!isActiveUser(user)) {
                audit(normalized + "登录", user.getUsername(), request, false, "inactive user", begin);
                return ApiResponse.error(ErrorCode.FORBIDDEN, "账号已禁用，请联系管理员");
            }
            List<GrantedAuthority> authorities = getAuthoritiesFromUser(user);
            // Token 仅包含用户身份，权限从数据库动态获取
            String token = jwtTokenProvider.generateToken(user.getUsername());
            String refresh = jwtTokenProvider.generateRefreshToken(user.getUsername());
            LoginResponse resp = new LoginResponse();
            resp.setToken(token);
            resp.setRefreshToken(refresh);
            resp.setUsername(user.getUsername());
            resp.setRoles(authorities.stream().map(GrantedAuthority::getAuthority).filter(a->a.startsWith("ROLE_")).collect(Collectors.toList()));
            java.util.Date exp = jwtTokenProvider.getExpiration(refresh);
            if (exp != null) {
                long ttl = exp.getTime() - System.currentTimeMillis();
                if (!authTokenService.storeRefreshToken(user.getUsername(), refresh, ttl)) {
                    audit(normalized + "登录", user.getUsername(), request, false, "refresh token persistence failed", begin);
                    return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "第三方登录失败，请稍后重试");
                }
            } else {
                audit(normalized + "登录", user.getUsername(), request, false, "refresh token expiration missing", begin);
                return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "第三方登录失败，请稍后重试");
            }
            audit(normalized + "登录", user.getUsername(), request, true, null, begin);
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("oauth callback error for platform: {}", platform, e);
            audit(platform + "登录", null, request, false, e.getMessage(), begin);
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "第三方登录失败，请稍后重试");
        }
    }

    // WeCom JS-SDK signatures
    @GetMapping("/wecom/jsapi-signature")
    public ApiResponse<Map<String, String>> wecomJsapi(@RequestParam String url) {
        WeComAuthService.SignOut s = weComAuthService.jsapiSignature(url);
        Map<String, String> out = new HashMap<>();
        out.put("timestamp", s.getTimestamp()); out.put("nonceStr", s.getNonceStr()); out.put("signature", s.getSignature());
        if (s.getCorpId() != null) out.put("corpId", s.getCorpId());
        return ApiResponse.success(out);
    }

    @GetMapping("/wecom/agent-jsapi-signature")
    public ApiResponse<Map<String, String>> wecomAgentJsapi(@RequestParam String url) {
        WeComAuthService.SignOut s = weComAuthService.agentJsapiSignature(url);
        Map<String, String> out = new HashMap<>();
        out.put("timestamp", s.getTimestamp()); out.put("nonceStr", s.getNonceStr()); out.put("signature", s.getSignature());
        if (s.getCorpId() != null) out.put("corpId", s.getCorpId());
        if (s.getAgentId() != null) out.put("agentId", s.getAgentId());
        return ApiResponse.success(out);
    }

    // 刷新令牌
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestBody com.yiyundao.compensation.interfaces.dto.auth.RefreshRequest req) {
        String refreshToken = req == null ? null : req.getRefreshToken();
        if (!StringUtils.hasText(refreshToken)) return ApiResponse.error(ErrorCode.PARAM_MISSING, "缺少refreshToken");
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            return ApiResponse.error(ErrorCode.REFRESH_TOKEN_INVALID, "refreshToken无效");
        }
        String username = authTokenService.consumeRefreshToken(refreshToken);
        if (!StringUtils.hasText(username)) return ApiResponse.error(ErrorCode.REFRESH_TOKEN_INVALID, "refreshToken已失效");
        SysUser user = sysUserService.findByUsername(username);
        if (user == null) return ApiResponse.error(ErrorCode.UNAUTHORIZED, "用户不存在");
        if (!isActiveUser(user)) return ApiResponse.error(ErrorCode.FORBIDDEN, "账号已禁用，请联系管理员");
        List<GrantedAuthority> authorities = getAuthoritiesFromUser(user);
        // Token 仅包含用户身份，权限从数据库动态获取
        String newAccess = jwtTokenProvider.generateToken(user.getUsername());
        String newRefresh = jwtTokenProvider.generateRefreshToken(user.getUsername());
        // 轮换刷新令牌
        java.util.Date exp = jwtTokenProvider.getExpiration(newRefresh);
        if (exp != null) {
            long ttl = exp.getTime() - System.currentTimeMillis();
            if (!authTokenService.storeRefreshToken(user.getUsername(), newRefresh, ttl)) {
                return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "刷新令牌签发失败，请重新登录");
            }
        } else {
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "刷新令牌签发失败，请重新登录");
        }
        LoginResponse resp = new LoginResponse();
        resp.setToken(newAccess);
        resp.setRefreshToken(newRefresh);
        resp.setUsername(user.getUsername());
        resp.setRoles(authorities.stream().map(GrantedAuthority::getAuthority).filter(a->a.startsWith("ROLE_")).collect(Collectors.toList()));
        return ApiResponse.success(resp);
    }

    // 登出：将当前访问token与refreshToken加入黑名单/删除
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) com.yiyundao.compensation.interfaces.dto.auth.LogoutRequest req,
                                    HttpServletRequest request) {
        boolean failed = false;
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            String token = bearer.substring(7);
            java.util.Date exp = jwtTokenProvider.getExpiration(token);
            long ttl = exp != null ? exp.getTime() - System.currentTimeMillis() : 0L;
            failed = !authTokenService.blacklistToken(token, ttl);
        }
        if (req != null && StringUtils.hasText(req.getRefreshToken())) {
            failed = !authTokenService.deleteRefreshToken(req.getRefreshToken()) || failed;
        }
        if (failed) {
            return ApiResponse.error(ErrorCode.SYSTEM_ERROR, "登出未完全完成，请稍后重试");
        }
        return ApiResponse.success(null);
    }

    private List<GrantedAuthority> getAuthoritiesFromUser(SysUser user) {
        List<GrantedAuthority> list = new ArrayList<>();
        Set<String> roleCodes;
        if (user != null && user.getId() != null) {
            roleCodes = userRoleService.getUserRoleCodes(user.getId());
        } else {
            roleCodes = Collections.emptySet();
        }
        for (String r : roleCodes) {
            if (StringUtils.hasText(r)) {
                String role = r.trim();
                if (!role.startsWith("ROLE_")) role = "ROLE_" + role;
                list.add(new SimpleGrantedAuthority(role));
            }
        }
        return list;
    }

    private boolean isActiveUser(SysUser user) {
        return user != null && UserStatus.ACTIVE.equals(user.getStatus());
    }

    private void audit(String op, String username, HttpServletRequest req, boolean success, String err, long begin) {
        try {
            // 记录请求参数（脱敏处理）
            String requestParams = null;
            if ("用户登录".equals(op) && username != null) {
                // 登录时只记录用户名，不记录密码
                requestParams = "{\"username\":\"" + username + "\"}";
            } else {
                // 其他操作可以记录完整参数
                requestParams = req.getQueryString();
            }
            auditLogService.record(op, req.getMethod(), req.getRequestURI(), req.getRemoteAddr(), req.getHeader("User-Agent"),
                    "AUTH", username, username, requestParams, success ? "OK" : "FAILED", err, System.currentTimeMillis() - begin);
        } catch (Exception ignore) {}
    }

    private String normalizeOAuthPlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return null;
        }
        String normalized = platform.trim().toLowerCase();
        if ("wecom".equals(normalized)) {
            return null;
        }
        return switch (normalized) {
            case "wechat", "dingtalk", "feishu" -> normalized;
            default -> null;
        };
    }
}
