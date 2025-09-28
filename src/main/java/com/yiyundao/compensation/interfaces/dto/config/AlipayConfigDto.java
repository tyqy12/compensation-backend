package com.yiyundao.compensation.interfaces.dto.config;

import lombok.Data;

@Data
public class AlipayConfigDto {
    private String appId;
    private String serverUrl;
    private String privateKey;
    private String publicKey;
    private String charset;
    private String signType;
    private String format;
    private String notifyUrl;
    private String returnUrl;
}

