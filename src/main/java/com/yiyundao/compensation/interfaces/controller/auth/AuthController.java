package com.yiyundao.compensation.interfaces.controller.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.interfaces.dto.auth.LoginRequest;
import com.yiyundao.compensation.interfaces.dto.auth.LoginResponse;
import com.yiyundao.compensation.interfaces.dto.auth.OAuthAuthorizeResponse;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.JwtTokenProvider;
import com.yiyundao.compensation.service.PlatformOAuthService;
import com.yiyundao.compensation.service.WeComAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PlatformOAuthService platformOAuthService;
    private final WeComAuthService weComAuthService;
    private final AuditLogService auditLogService;
    private final com.yiyundao.compensation.service.AuthTokenService authTokenService;
    private final com.yiyundao.compensation.service.LoginRateLimiterService loginRateLimiterService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        try {
            if (!StringUtils.hasText(req.getUsername()) || !StringUtils.hasText(req.getPassword())) {
                return ApiResponse.error(400, "用户名或密码为空");
            }
            String ip = request.getRemoteAddr();
            if (loginRateLimiterService.isLocked(req.getUsername(), ip)) {
                audit("LOGIN_PASSWORD", req.getUsername(), request, false, "rate-limited", begin);
                return ApiResponse.error(429, "尝试过于频繁，请稍后再试");
            }
            SysUser user = sysUserService.getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
            if (user == null || !StringUtils.hasText(user.getPassword()) || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
                loginRateLimiterService.onFail(req.getUsername(), ip);
                audit("LOGIN_PASSWORD", req.getUsername(), request, false, "bad credentials", begin);
                return ApiResponse.error(401, "用户名或密码错误");
            }
            List<GrantedAuthority> authorities = parseRoles(user.getRoles());
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities);
            String token = jwtTokenProvider.generateToken(auth);
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
                authTokenService.storeRefreshToken(user.getUsername(), refresh, ttl);
            }
            loginRateLimiterService.onSuccess(user.getUsername(), ip);
            audit("LOGIN_PASSWORD", user.getUsername(), request, true, null, begin);
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("password login error", e);
            audit("LOGIN_PASSWORD", req.getUsername(), request, false, e.getMessage(), begin);
            return ApiResponse.error("登录失败");
        }
    }

    @GetMapping("/oauth/authorize")
    public ApiResponse<OAuthAuthorizeResponse> authorize(@RequestParam String platform,
                                                         @RequestParam(required = false, defaultValue = "web") String channel,
                                                         @RequestParam String redirectUri) {
        if ("wecom".equalsIgnoreCase(platform) || "wechat".equalsIgnoreCase(platform)) {
            WeComAuthService.AuthorizeOut a = weComAuthService.buildAuthorize(channel, redirectUri);
            OAuthAuthorizeResponse out = new OAuthAuthorizeResponse();
            out.setUrl(a.getUrl());
            out.setState(a.getState());
            return ApiResponse.success(out);
        }
        PlatformOAuthService.Authorize a = platformOAuthService.buildAuthorize(platform, redirectUri);
        OAuthAuthorizeResponse out = new OAuthAuthorizeResponse();
        out.setUrl(a.getUrl());
        out.setState(a.getState());
        return ApiResponse.success(out);
    }

    @GetMapping("/oauth/callback/{platform}")
    public ApiResponse<LoginResponse> oauthCallback(@PathVariable String platform,
                                                    @RequestParam String code,
                                                    @RequestParam(required = false) String state,
                                                    HttpServletRequest request) {
        long begin = System.currentTimeMillis();
        try {
            // 校验并消费state，失败拒绝
            if (!StringUtils.hasText(state) || !authTokenService.consumeOAuthState(platform, state)) {
                audit("LOGIN_OAUTH_" + platform.toUpperCase(), null, request, false, "invalid state", begin);
                return ApiResponse.error(400, "非法请求(state)");
            }
            // 企业微信专用回调
            if ("wecom".equalsIgnoreCase(platform) || "wechat".equalsIgnoreCase(platform)) {
                WeComAuthService.TokenUser tuser = weComAuthService.exchangeCode(code, state);
                if (tuser == null || !StringUtils.hasText(tuser.getUserid())) {
                    audit("LOGIN_OAUTH_WECOM", null, request, false, "exchange failed", begin);
                    return ApiResponse.error(403, "需要先完成账号绑定");
                }
                SysUser user = sysUserService.findByPlatform("wechat", tuser.getUserid());
                if (user == null) user = sysUserService.findByPlatform("wecom", tuser.getUserid());
                if (user == null) {
                    audit("LOGIN_OAUTH_WECOM", null, request, false, "not bound", begin);
                    return ApiResponse.error(403, "需要先完成账号绑定");
                }
                List<GrantedAuthority> authorities = parseRoles(user.getRoles());
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities);
                String token = jwtTokenProvider.generateToken(auth);
                String refresh = jwtTokenProvider.generateRefreshToken(user.getUsername());
                LoginResponse resp = new LoginResponse();
                resp.setToken(token); resp.setRefreshToken(refresh);
                resp.setUsername(user.getUsername());
                resp.setRoles(authorities.stream().map(GrantedAuthority::getAuthority).filter(a->a.startsWith("ROLE_")).collect(Collectors.toList()));
                java.util.Date exp = jwtTokenProvider.getExpiration(refresh);
                if (exp != null) { long ttl = exp.getTime() - System.currentTimeMillis(); authTokenService.storeRefreshToken(user.getUsername(), refresh, ttl); }
                audit("LOGIN_OAUTH_WECOM", user.getUsername(), request, true, null, begin);
                return ApiResponse.success(resp);
            }
            PlatformOAuthService.PlatformUser puser = platformOAuthService.exchangeCode(platform, code);
            if (puser == null || !StringUtils.hasText(puser.getPlatformUserId())) {
                audit("LOGIN_OAUTH_" + platform.toUpperCase(), null, request, false, "exchange failed", begin);
                return ApiResponse.error(401, "第三方登录失败");
            }
            // 找绑定用户
            SysUser user = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPlatformType, platform)
                    .eq(SysUser::getPlatformUserId, puser.getPlatformUserId())
                    .last("limit 1"));
            if (user == null) {
                audit("LOGIN_OAUTH_" + platform.toUpperCase(), null, request, false, "not bound", begin);
                return ApiResponse.error(403, "未绑定该第三方账号，请联系管理员绑定后再登录");
            }
            List<GrantedAuthority> authorities = parseRoles(user.getRoles());
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities);
            String token = jwtTokenProvider.generateToken(auth);
            String refresh = jwtTokenProvider.generateRefreshToken(user.getUsername());
            LoginResponse resp = new LoginResponse();
            resp.setToken(token);
            resp.setRefreshToken(refresh);
            resp.setUsername(user.getUsername());
            resp.setRoles(authorities.stream().map(GrantedAuthority::getAuthority).filter(a->a.startsWith("ROLE_")).collect(Collectors.toList()));
            java.util.Date exp = jwtTokenProvider.getExpiration(refresh);
            if (exp != null) {
                long ttl = exp.getTime() - System.currentTimeMillis();
                authTokenService.storeRefreshToken(user.getUsername(), refresh, ttl);
            }
            audit("LOGIN_OAUTH_" + platform.toUpperCase(), user.getUsername(), request, true, null, begin);
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("oauth callback error", e);
            audit("LOGIN_OAUTH_" + platform.toUpperCase(), null, request, false, e.getMessage(), begin);
            return ApiResponse.error("登录失败");
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
        String refreshToken = req.getRefreshToken();
        if (!StringUtils.hasText(refreshToken)) return ApiResponse.error(400, "缺少refreshToken");
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            return ApiResponse.error(401, "refreshToken无效");
        }
        String username = authTokenService.getRefreshOwner(refreshToken);
        if (!StringUtils.hasText(username)) return ApiResponse.error(401, "refreshToken已失效");
        SysUser user = sysUserService.findByUsername(username);
        if (user == null) return ApiResponse.error(401, "用户不存在");
        List<GrantedAuthority> authorities = parseRoles(user.getRoles());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities);
        String newAccess = jwtTokenProvider.generateToken(auth);
        String newRefresh = jwtTokenProvider.generateRefreshToken(user.getUsername());
        // 轮换刷新令牌
        authTokenService.deleteRefreshToken(refreshToken);
        java.util.Date exp = jwtTokenProvider.getExpiration(newRefresh);
        if (exp != null) {
            long ttl = exp.getTime() - System.currentTimeMillis();
            authTokenService.storeRefreshToken(user.getUsername(), newRefresh, ttl);
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
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            String token = bearer.substring(7);
            java.util.Date exp = jwtTokenProvider.getExpiration(token);
            long ttl = exp != null ? exp.getTime() - System.currentTimeMillis() : 0L;
            authTokenService.blacklistToken(token, ttl);
        }
        if (req != null && StringUtils.hasText(req.getRefreshToken())) {
            authTokenService.deleteRefreshToken(req.getRefreshToken());
        }
        return ApiResponse.success(null);
    }

    private List<GrantedAuthority> parseRoles(String roles) {
        List<GrantedAuthority> list = new ArrayList<>();
        if (StringUtils.hasText(roles)) {
            for (String r : roles.split(",")) {
                if (StringUtils.hasText(r)) {
                    String role = r.trim();
                    if (!role.startsWith("ROLE_")) role = "ROLE_" + role;
                    list.add(new SimpleGrantedAuthority(role));
                }
            }
        } else {
            list.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return list;
    }

    private void audit(String op, String username, HttpServletRequest req, boolean success, String err, long begin) {
        try {
            auditLogService.record(op, req.getMethod(), req.getRequestURI(), req.getRemoteAddr(), req.getHeader("User-Agent"),
                    "AUTH", username, username, null, success ? "OK" : "FAILED", err, System.currentTimeMillis() - begin);
        } catch (Exception ignore) {}
    }
}
