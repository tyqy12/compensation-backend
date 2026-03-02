package com.yiyundao.compensation.interfaces.dto.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class YunzhanghuConfigDto {

    @JsonAlias({"dealer_id"})
    private String dealerId;

    @JsonAlias({"broker_id"})
    private String brokerId;

    @JsonAlias({"app_key"})
    private String appKey;

    @JsonAlias({"3desKey", "3des_key", "threeDesKey"})
    private String des3Key;

    @JsonAlias({"rsa_private_key"})
    private String rsaPrivateKey;

    @JsonAlias({"rsa_public_key"})
    private String rsaPublicKey;

    @JsonAlias({"sign_type"})
    private String signType;

    private String url;
    private String notifyUrl;
    private String projectId;
    private String checkName;
    private String dealerPlatformName;
    private Boolean isDebug;
}
