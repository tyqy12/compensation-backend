package com.yiyundao.compensation.common.config;

/*
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
*/
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Data
@Configuration
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {

    private String appId;
    private String serverUrl;
    private String privateKey;
    private String publicKey;
    private String charset;
    private String signType;
    private String format;
    private String notifyUrl;
    private String returnUrl;

    /*
    @Bean
    @Profile({"dev", "staging"})
    public AlipayClient alipayClient() {
        return new DefaultAlipayClient(
            serverUrl,
            appId,
            privateKey,
            format,
            charset,
            publicKey,
            signType
        );
    }
    */
}

