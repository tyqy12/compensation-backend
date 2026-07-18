package com.yiyundao.compensation.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.yiyundao.compensation.interfaces.dto.config.DingTalkConfigDto;
import com.yiyundao.compensation.interfaces.dto.config.FeishuConfigDto;
import com.yiyundao.compensation.interfaces.dto.config.WechatConfigDto;
import com.yiyundao.compensation.common.utils.SecretLogSanitizer;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformOAuthService {

    private final IntegrationConfigService integrationConfigService;
    private final WebClient webClient;
    private final AuthTokenService authTokenService;
    private final com.yiyundao.compensation.modules.system.service.SysConfigService sysConfigService;
    private final TrustedRedirectUrlValidator trustedRedirectUrlValidator;

    public Authorize buildAuthorize(String platform, String redirectUri) {
        return buildAuthorize(platform, redirectUri, null);
    }

    public Authorize buildAuthorize(String platform, String redirectUri, String channel) {
        validateRedirectUri(redirectUri);
        String resolvedChannel = resolveChannel(platform, channel);
        String state = UUID.randomUUID().toString().replaceAll("-", "");
        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String url;
        switch (platform) {
            case "wechat": {
                WechatConfigDto cfg = integrationConfigService.getWechatConfig();
                if (cfg == null || !StringUtils.hasText(cfg.getCorpId()) || !StringUtils.hasText(cfg.getAgentId())) {
                    throw new IllegalStateException("wechat config missing");
                }
                if ("wecom".equals(resolvedChannel)) {
                    url = "https://open.weixin.qq.com/connect/oauth2/authorize?appid=" + cfg.getCorpId()
                            + "&redirect_uri=" + encodedRedirect
                            + "&response_type=code&scope=snsapi_base&state=" + state
                            + "&agentid=" + cfg.getAgentId() + "#wechat_redirect";
                } else {
                    url = "https://open.work.weixin.qq.com/wwopen/sso/qrConnect?appid=" + cfg.getCorpId()
                            + "&agentid=" + cfg.getAgentId() + "&state=" + state + "&redirect_uri=" + encodedRedirect;
                }
                break;
            }
            case "dingtalk": {
                DingTalkConfigDto cfg = integrationConfigService.getDingTalkConfig();
                if (cfg == null || !StringUtils.hasText(cfg.getAppKey())) {
                    throw new IllegalStateException("dingtalk config missing");
                }
                // 使用新版 OAuth2 授权地址
                url = "https://login.dingtalk.com/oauth2/auth?response_type=code&client_id=" + cfg.getAppKey()
                        + "&scope=openid&prompt=consent&state=" + state + "&redirect_uri=" + encodedRedirect;
                break;
            }
            case "feishu": {
                FeishuConfigDto cfg = integrationConfigService.getFeishuConfig();
                if (cfg == null || !StringUtils.hasText(cfg.getAppId())) {
                    throw new IllegalStateException("feishu config missing");
                }
                url = "https://open.feishu.cn/open-apis/authen/v1/index?app_id=" + cfg.getAppId()
                        + "&state=" + state + "&redirect_uri=" + encodedRedirect;
                break;
            }
            default:
                throw new IllegalArgumentException("unsupported platform: " + platform);
        }
        // 保存 state（TTL可配置，默认5分钟）
        int ttl = sysConfigService.getInt("auth.oauth.state.ttl.seconds", 300);
        try { authTokenService.storeOAuthState(platform, state, ttl); } catch (Exception ignore) {}
        Authorize a = new Authorize();
        a.setState(state);
        a.setUrl(url);
        a.setChannel(resolvedChannel);
        return a;
    }

    private String resolveChannel(String platform, String channel) {
        if ("wechat".equals(platform) && "wecom".equalsIgnoreCase(StringUtils.trimWhitespace(channel))) {
            return "wecom";
        }
        return "web";
    }

    private void validateRedirectUri(String redirectUri) {
        try {
            trustedRedirectUrlValidator.validateTrustedHttpUrl(redirectUri, "redirectUri");
        } catch (Exception e) {
            throw new IllegalArgumentException("redirectUri validation failed: " + e.getMessage());
        }
    }

    public PlatformUser exchangeCode(String platform, String code) {
        switch (platform) {
            case "wechat":
                return wechatExchange(code);
            case "dingtalk":
                return dingtalkExchange(code);
            case "feishu":
                return feishuExchange(code);
            default:
                throw new IllegalArgumentException("unsupported platform: " + platform);
        }
    }

    private PlatformUser wechatExchange(String code) {
        try {
            // 1) 获取 access_token
            WechatConfigDto cfg = integrationConfigService.getWechatConfig();
            if (cfg == null || !StringUtils.hasText(cfg.getCorpId()) || !StringUtils.hasText(cfg.getCorpSecret())) {
                log.warn("wechat exchange code failed: wechat config missing or incomplete");
                return null;
            }
            String tokenUrl = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + cfg.getCorpId() + "&corpsecret=" + cfg.getCorpSecret();
            WechatTokenResp token = webClient.get().uri(tokenUrl).retrieve().bodyToMono(WechatTokenResp.class).block();
            if (token == null || token.getErrcode() != 0) {
                log.warn("wechat gettoken failed: errcode={}, errmsg={}",
                        token == null ? null : token.getErrcode(),
                        token == null ? "null response" : SecretLogSanitizer.sanitize(token.getErrmsg()));
                return null;
            }
            // 2) 通过 code 换 userid
            String infoUrl = "https://qyapi.weixin.qq.com/cgi-bin/user/getuserinfo?access_token=" + token.getAccess_token() + "&code=" + code;
            WechatUserInfoResp info = webClient.get().uri(infoUrl).retrieve().bodyToMono(WechatUserInfoResp.class).block();
            if (info != null && info.getErrcode() == 0 && StringUtils.hasText(info.getUserid())) {
                PlatformUser u = new PlatformUser();
                u.setProvider("wechat");
                u.setTenantKey(cfg.getCorpId());
                u.setSubjectType("user_id");
                u.setSubjectId(info.getUserid());
                return u;
            }
            log.warn("wechat getuserinfo failed: errcode={}, errmsg={}",
                    info == null ? null : info.getErrcode(),
                    info == null ? "null response" : SecretLogSanitizer.sanitize(info.getErrmsg()));
        } catch (Exception e) {
            log.error("wechat exchange code failed: {}", SecretLogSanitizer.sanitize(e));
        }
        return null;
    }

    private PlatformUser dingtalkExchange(String code) {
        try {
            DingTalkConfigDto cfg = integrationConfigService.getDingTalkConfig();
            if (cfg == null) return null;
            String url = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
            DingOAuthTokenReq body = new DingOAuthTokenReq(cfg.getAppKey(), cfg.getAppSecret(), code, "authorization_code");
            DingOAuthTokenResp resp = webClient.post().uri(url).bodyValue(body).retrieve().bodyToMono(DingOAuthTokenResp.class).block();
            if (resp != null && resp.getAccessToken() != null) {
                PlatformUser u = new PlatformUser();
                u.setProvider("dingtalk");
                u.setTenantKey(cfg.getAppKey());
                // 优先使用 openId 作为平台唯一标识
                String pid = resp.getOpenId() != null ? resp.getOpenId() : resp.getUnionId();
                u.setSubjectType(resp.getOpenId() != null ? "open_id" : "union_id");
                u.setSubjectId(pid);
                return u;
            }
        } catch (Exception e) {
            log.error("dingtalk exchange code failed: {}", SecretLogSanitizer.sanitize(e));
        }
        return null;
    }

    private PlatformUser feishuExchange(String code) {
        try {
            FeishuConfigDto cfg = integrationConfigService.getFeishuConfig();
            String url = "https://open.feishu.cn/open-apis/authen/v1/access_token";
            FeishuTokenReq body = new FeishuTokenReq("authorization_code", code);
            String basic = java.util.Base64.getEncoder().encodeToString((cfg.getAppId() + ":" + cfg.getAppSecret()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            FeishuTokenResp resp = webClient.post().uri(url)
                    .headers(h -> h.set("Authorization", "Basic " + basic))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(FeishuTokenResp.class)
                    .block();
            if (resp != null && resp.getCode() == 0 && resp.getData() != null && StringUtils.hasText(resp.getData().getUser_id())) {
                PlatformUser u = new PlatformUser();
                u.setProvider("feishu");
                u.setTenantKey(cfg.getAppId());
                u.setSubjectType("user_id");
                u.setSubjectId(resp.getData().getUser_id());
                return u;
            }
        } catch (Exception e) {
            log.error("feishu exchange code failed: {}", SecretLogSanitizer.sanitize(e));
        }
        return null;
    }

    @Data
    public static class PlatformUser {
        private String provider;
        private String tenantKey;
        private String subjectType;
        private String subjectId;
        private String displayName;

        @Deprecated
        public String getPlatform() {
            return provider;
        }

        @Deprecated
        public void setPlatform(String platform) {
            this.provider = platform;
        }

        @Deprecated
        public String getPlatformUserId() {
            return subjectId;
        }

        @Deprecated
        public void setPlatformUserId(String platformUserId) {
            this.subjectId = platformUserId;
        }
    }

    @Data
    public static class Authorize {
        private String url;
        private String state;
        private String channel;
    }

    @Data
    private static class WechatTokenResp { private int errcode; private String errmsg; private String access_token; }
    @Data
    private static class WechatUserInfoResp {
        private int errcode;
        private String errmsg;
        @JsonAlias({"UserId", "userid"})
        private String userid;
    }

    @Data
    private static class FeishuTokenReq { private String grant_type; private String code; public FeishuTokenReq(String g, String c){this.grant_type=g;this.code=c;} }
    @Data
    private static class FeishuTokenResp { private int code; private String msg; private FeishuTokenData data; }
    @Data
    private static class FeishuTokenData { private String access_token; private String refresh_token; private String user_id; }

    @Data
    private static class DingOAuthTokenReq { private String clientId; private String clientSecret; private String code; private String grantType; public DingOAuthTokenReq(String id, String sec, String code, String gt){this.clientId=id;this.clientSecret=sec;this.code=code;this.grantType=gt;} }
    @Data
    private static class DingOAuthTokenResp { private String accessToken; private String refreshToken; private String openId; private String unionId; }
}
