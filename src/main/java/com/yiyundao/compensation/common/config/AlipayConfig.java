package com.yiyundao.compensation.common.config;

/*
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
*/
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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

    // 增强安全配置
    private String encryptKey;           // 敏感数据加密密钥
    private String certMode = "publicKey"; // 密钥模式：publicKey / cert
    private String appCertPath;          // 应用公钥证书路径
    private String alipayCertPath;       // 支付宝公钥证书路径
    private String alipayRootCertPath;   // 支付宝根证书路径

    // 请求超时配置（毫秒）
    private int connectTimeout = 3000;   // 连接超时 3秒
    private int readTimeout = 10000;     // 读取超时 10秒

    // 业务限制配置
    private java.math.BigDecimal singleLimit = new java.math.BigDecimal("10000");  // 单笔限额
    private java.math.BigDecimal dailyLimit = new java.math.BigDecimal("100000");  // 日累计限额
    private boolean realNameVerify = false;  // 是否校验收款人姓名

    /*
    @Bean
    @Profile({"dev", "staging"})
    public AlipayClient alipayClient() {
        return new DefaultAlipayClient(
            serverUrl,
            appId,
            privateKey,
            format != null ? format : "json",
            charset != null ? charset : "UTF-8",
            publicKey,
            signType != null ? signType : "RSA2",
            connectTimeout,
            readTimeout
        );
    }
    */
}

