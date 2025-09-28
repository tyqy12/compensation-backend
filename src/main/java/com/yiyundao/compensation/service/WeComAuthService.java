package com.yiyundao.compensation.service;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.interfaces.dto.config.WechatConfigDto;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yiyundao.compensation.modules.system.service.SysConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeComAuthService {

    private final IntegrationConfigService integrationConfigService;
    private final SysConfigService sysConfigService;
    private final AuthTokenService authTokenService;
    private final PlatformTokenCacheService platformTokenCacheService;
    private final WebClient webClient;
    private final Environment env;

    private static final String PLATFORM = "wechat"; // internal platform code reuse
    private static final String API_BASE = "https://qyapi.weixin.qq.com/cgi-bin";

    @Data
    public static class AuthorizeOut { private String url; private String state; private String channel; }

    @Data
    public static class SignOut { private String timestamp; private String nonceStr; private String signature; private String corpId; private String agentId; }

    public AuthorizeOut buildAuthorize(String channel, String redirectUri) {
        WechatConfigDto cfg = getWeComConfig();
        String corpId = cfg != null ? cfg.getCorpId() : null;
        String agentId = cfg != null ? cfg.getAgentId() : null;
        String scope = Optional.ofNullable(env.getProperty("WECOM_LOGIN_SCOPE")).filter(StringUtils::hasText).orElse("snsapi_base");
        validateConfig(corpId, agentId);
        validateRedirectUri(redirectUri);

        String state = UUID.randomUUID().toString().replace("-", "");
        int ttl = Optional.ofNullable(sysConfigService.getInt("auth.oauth.state.ttl.seconds", null))
                .orElse(getIntEnv("CSRF_STATE_TTL_SECONDS", 300));
        authTokenService.storeOAuthState("wecom", state, ttl);

        String encoded = URI.create(redirectUri).toString();
        encoded = java.net.URLEncoder.encode(encoded, java.nio.charset.StandardCharsets.UTF_8);
        String url;
        if ("wecom".equalsIgnoreCase(channel)) {
            url = "https://open.weixin.qq.com/connect/oauth2/authorize?appid=" + corpId
                    + "&redirect_uri=" + encoded
                    + "&response_type=code&scope=" + scope
                    + "&state=" + state
                    + "&agentid=" + agentId + "#wechat_redirect";
        } else {
            url = "https://login.work.weixin.qq.com/wwlogin/sso/login?login_type=CorpApp&appid=" + corpId
                    + "&agentid=" + agentId + "&redirect_uri=" + encoded + "&state=" + state;
        }
        AuthorizeOut out = new AuthorizeOut();
        out.setUrl(url); out.setState(state); out.setChannel(channel);
        return out;
    }

    public TokenUser exchangeCode(String code, String state) {
        if (!StringUtils.hasText(state) || !authTokenService.consumeOAuthState("wecom", state)) {
            throw new IllegalArgumentException("invalid state");
        }
        String accessToken = getAccessToken();
        if (accessToken == null) {
            throw new IllegalStateException("wecom not configured");
        }
        String url = API_BASE + "/auth/getuserinfo?access_token=" + accessToken + "&code=" + code;
        WeComUserInfo resp = webClient.get().uri(url).retrieve().bodyToMono(WeComUserInfo.class).block();
        if (resp == null || resp.getErrcode() != 0) {
            throw new RuntimeException("wecom error: " + (resp != null ? resp.getErrmsg() : "null"));
        }
        TokenUser tu = new TokenUser();
        tu.setUserid(resp.getUserid());
        tu.setExternalUserid(resp.getExternal_userid());
        return tu;
    }

    public SignOut jsapiSignature(String url) {
        String ticket = getJsapiTicket();
        WechatConfigDto cfg = getWeComConfig();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String str = "jsapi_ticket=" + ticket + "&noncestr=" + nonce + "&timestamp=" + ts + "&url=" + url;
        String sig = sha1(str);
        SignOut out = new SignOut();
        out.setTimestamp(ts); out.setNonceStr(nonce); out.setSignature(sig);
        out.setCorpId(cfg != null ? cfg.getCorpId() : null);
        return out;
    }

    public SignOut agentJsapiSignature(String url) {
        String ticket = getAgentTicket();
        WechatConfigDto cfg = getWeComConfig();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String str = "jsapi_ticket=" + ticket + "&noncestr=" + nonce + "&timestamp=" + ts + "&url=" + url;
        String sig = sha1(str);
        SignOut out = new SignOut();
        out.setTimestamp(ts); out.setNonceStr(nonce); out.setSignature(sig);
        out.setCorpId(cfg != null ? cfg.getCorpId() : null);
        out.setAgentId(cfg != null ? cfg.getAgentId() : null);
        return out;
    }

    private String getAccessToken() {
        String cached = platformTokenCacheService.getToken(PLATFORM);
        if (StringUtils.hasText(cached)) return cached;
        WechatConfigDto cfg = getWeComConfig();
        if (cfg == null || !StringUtils.hasText(cfg.getCorpId()) || !StringUtils.hasText(cfg.getCorpSecret())) return null;
        String url = API_BASE + "/gettoken?corpid=" + cfg.getCorpId() + "&corpsecret=" + cfg.getCorpSecret();
        WeComToken r = webClient.get().uri(url).retrieve().bodyToMono(WeComToken.class).block();
        if (r != null && r.getErrcode() == 0 && StringUtils.hasText(r.getAccess_token())) {
            int skew = Optional.ofNullable(sysConfigService.getInt("oauth.token.ttl.buffer.seconds", null))
                    .orElse(getIntEnv("CACHE_WECHAT_TTL_SKEW_SECONDS", 120));
            long ttl = Math.max(60, (r.getExpires_in() != null ? r.getExpires_in() : 7200) - skew);
            platformTokenCacheService.setToken(PLATFORM, r.getAccess_token(), ttl);
            return r.getAccess_token();
        }
        log.warn("wecom gettoken failed: {}", r != null ? r.getErrmsg() : "null");
        return null;
    }

    private String getJsapiTicket() {
        String key = "wecom:jsapi_ticket";
        String cached = platformTokenCacheService.getToken(key);
        if (StringUtils.hasText(cached)) return cached;
        String token = getAccessToken();
        if (token == null) throw new IllegalStateException("wecom not configured");
        String url = API_BASE + "/get_jsapi_ticket?access_token=" + token;
        JsTicket r = webClient.get().uri(url).retrieve().bodyToMono(JsTicket.class).block();
        if (r != null && r.getErrcode() == 0 && StringUtils.hasText(r.getTicket())) {
            int skew = Optional.ofNullable(sysConfigService.getInt("oauth.token.ttl.buffer.seconds", null))
                    .orElse(getIntEnv("CACHE_WECHAT_TTL_SKEW_SECONDS", 120));
            long ttl = Math.max(60, (r.getExpires_in() != null ? r.getExpires_in() : 7200) - skew);
            platformTokenCacheService.setToken(key, r.getTicket(), ttl);
            return r.getTicket();
        }
        throw new RuntimeException("wecom jsapi ticket error: " + (r != null ? r.getErrmsg() : "null"));
    }

    private String getAgentTicket() {
        String key = "wecom:agent_jsapi_ticket";
        String cached = platformTokenCacheService.getToken(key);
        if (StringUtils.hasText(cached)) return cached;
        String token = getAccessToken();
        if (token == null) throw new IllegalStateException("wecom not configured");
        String url = API_BASE + "/ticket/get?access_token=" + token + "&type=agent_config";
        JsTicket r = webClient.get().uri(url).retrieve().bodyToMono(JsTicket.class).block();
        if (r != null && r.getErrcode() == 0 && StringUtils.hasText(r.getTicket())) {
            int skew = Optional.ofNullable(sysConfigService.getInt("oauth.token.ttl.buffer.seconds", null))
                    .orElse(getIntEnv("CACHE_WECHAT_TTL_SKEW_SECONDS", 120));
            long ttl = Math.max(60, (r.getExpires_in() != null ? r.getExpires_in() : 7200) - skew);
            platformTokenCacheService.setToken(key, r.getTicket(), ttl);
            return r.getTicket();
        }
        throw new RuntimeException("wecom agent ticket error: " + (r != null ? r.getErrmsg() : "null"));
    }

    private void validateConfig(String corpId, String agentId) {
        if (!StringUtils.hasText(corpId) || !StringUtils.hasText(agentId)) {
            throw new IllegalStateException("wecom config missing");
        }
        if (corpId.toLowerCase().startsWith("your_") || agentId.toLowerCase().startsWith("your_")) {
            throw new IllegalStateException("wecom config placeholders detected");
        }
    }

    private void validateRedirectUri(String redirectUri) {
        try {
            URI uri = URI.create(redirectUri);
            String host = uri.getHost();
            int port = uri.getPort();
            if (!StringUtils.hasText(host)) throw new IllegalArgumentException("redirectUri invalid");
            String allow = sysConfigService.getString("oauth.trusted.redirect.hosts", null);
            if (!StringUtils.hasText(allow)) allow = env.getProperty("OAUTH_TRUSTED_REDIRECT_HOSTS");
            if (!StringUtils.hasText(allow)) throw new IllegalStateException("trusted hosts not configured");
            Set<String> hosts = new HashSet<>();
            for (String it : allow.split(",")) {
                if (StringUtils.hasText(it)) hosts.add(it.trim());
            }
            String hp = port > 0 ? host + ":" + port : host;
            if (!(hosts.contains(hp) || hosts.contains(host))) {
                throw new IllegalArgumentException("redirect host not trusted");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("redirectUri validation failed: " + e.getMessage());
        }
    }

    private WechatConfigDto getWeComConfig() {
        WechatConfigDto cfg = integrationConfigService.getWechatConfig();
        if (cfg == null || !StringUtils.hasText(cfg.getCorpId())) {
            String id = env.getProperty("WECOM_CORP_ID");
            String agentId = env.getProperty("WECOM_AGENT_ID");
            String secret = env.getProperty("WECOM_CORP_SECRET");
            if (StringUtils.hasText(id)) {
                WechatConfigDto c = new WechatConfigDto();
                c.setCorpId(id); c.setAgentId(agentId); c.setCorpSecret(secret);
                return c;
            }
        }
        return cfg;
    }

    private int getIntEnv(String key, int def) {
        try { return Integer.parseInt(Objects.toString(env.getProperty(key), "" + def)); } catch (Exception e) { return def; }
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] out = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Data
    private static class WeComToken { private int errcode; private String errmsg; private String access_token; private Integer expires_in; }
    @Data
    private static class WeComUserInfo { private int errcode; private String errmsg; private String userid; private String openid; private String external_userid; }
    @Data
    private static class JsTicket { private int errcode; private String errmsg; private String ticket; private Integer expires_in; }

    @Data
    public static class TokenUser { private String userid; private String externalUserid; }
}
