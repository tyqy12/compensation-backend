package com.yiyundao.compensation.interfaces.dto.config;

import lombok.Data;
import java.math.BigDecimal;

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

    // 建议添加：增强安全配置
    private String encryptKey;           // 敏感数据加密密钥（可选，AES加密需要32位密钥）
    private String encryptType;          // 加密类型：固定值 "AES"（启用接口内容加密时必填）
    private String certMode;             // 密钥模式：publicKey(默认) / cert(证书模式）
    private String appCertPath;          // 应用公钥证书路径（证书模式必需）
    private String alipayCertPath;       // 支付宝公钥证书路径（证书模式必需）
    private String alipayRootCertPath;   // 支付宝根证书路径（证书模式必需）

    // 建议添加：请求超时配置
    private Integer connectTimeout;      // 连接超时（毫秒，默认3000）
    private Integer readTimeout;         // 读取超时（毫秒，默认10000）

    // 建议添加：业务限制配置
    private BigDecimal singleLimit;      // 单笔限额（默认10000元）
    private BigDecimal dailyLimit;       // 日累计限额（默认100000元）
    private Boolean realNameVerify;      // 是否校验收款人姓名
}

