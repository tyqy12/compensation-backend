package com.yiyundao.compensation.service;

import com.yiyundao.compensation.interfaces.dto.config.YunzhanghuConfigDto;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yunzhanghu.sdk.YzhException;
import com.yunzhanghu.sdk.base.YzhConfig;
import com.yunzhanghu.sdk.base.YzhRequest;
import com.yunzhanghu.sdk.base.YzhResponse;
import com.yunzhanghu.sdk.notify.NotifyClient;
import com.yunzhanghu.sdk.notify.domain.NotifyRequest;
import com.yunzhanghu.sdk.notify.domain.NotifyResponse;
import com.yunzhanghu.sdk.payment.PaymentClient;
import com.yunzhanghu.sdk.payment.domain.CreateAlipayOrderRequest;
import com.yunzhanghu.sdk.payment.domain.CreateAlipayOrderResponse;
import com.yunzhanghu.sdk.payment.domain.GetOrderRequest;
import com.yunzhanghu.sdk.payment.domain.GetOrderResponse;
import com.yunzhanghu.sdk.payment.domain.NotifyOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class YunzhanghuClient {

    private final IntegrationConfigService integrationConfigService;

    public YzhResponse<CreateAlipayOrderResponse> createAlipayOrder(String orderId,
                                                                     BigDecimal amount,
                                                                     String realName,
                                                                     String alipayAccount,
                                                                     String idCardNo,
                                                                     String phoneNo,
                                                                     String remark,
                                                                     String dealerUserId,
                                                                     String dealerUserNickname) throws YzhException {
        YunzhanghuConfigDto config = getRequiredConfig();
        requireText(config.getDealerPlatformName(), "dealerPlatformName");
        CreateAlipayOrderRequest request = new CreateAlipayOrderRequest();
        request.setOrderId(orderId);
        request.setDealerId(config.getDealerId());
        request.setBrokerId(config.getBrokerId());
        request.setRealName(realName);
        request.setCardNo(alipayAccount);
        request.setIdCard(idCardNo);
        request.setPhoneNo(phoneNo);
        request.setPay(amount == null ? null : amount.toPlainString());
        request.setPayRemark(remark);
        request.setNotifyUrl(config.getNotifyUrl());
        request.setProjectId(config.getProjectId());
        request.setDealerPlatformName(config.getDealerPlatformName());
        request.setDealerUserId(dealerUserId);
        request.setDealerUserNickname(dealerUserNickname);
        if (StringUtils.hasText(config.getCheckName())) {
            request.setCheckName(config.getCheckName());
        }

        return createPaymentClient().createAlipayOrder(YzhRequest.build(request));
    }

    public YzhResponse<GetOrderResponse> queryOrder(String orderId) throws YzhException {
        GetOrderRequest request = new GetOrderRequest();
        request.setOrderId(orderId);
        // 云账户实时支付查单接口要求传支付路径中文名称，而不是回调中的英文渠道编码。
        request.setChannel("支付宝");
        return createPaymentClient().getOrder(YzhRequest.build(request));
    }

    public NotifyResponse<NotifyOrderRequest> decodeOrderNotify(Map<String, String> callbackParams) {
        NotifyRequest notifyRequest = new NotifyRequest();
        notifyRequest.setData(callbackParams == null ? null : callbackParams.get("data"));
        notifyRequest.setMess(callbackParams == null ? null : callbackParams.get("mess"));
        notifyRequest.setTimestamp(callbackParams == null ? null : callbackParams.get("timestamp"));
        notifyRequest.setSign(callbackParams == null ? null : callbackParams.get("sign"));
        return createNotifyClient().notifyDecoder(notifyRequest, NotifyOrderRequest.class);
    }

    public boolean healthCheck() {
        try {
            YunzhanghuConfigDto config = getRequiredConfig();
            requireText(config.getDealerPlatformName(), "dealerPlatformName");
            buildConfig();
            return true;
        } catch (Exception ex) {
            log.warn("云账户配置校验失败: {}", ex.getMessage());
            return false;
        }
    }

    public YzhConfig buildConfig() {
        YunzhanghuConfigDto config = getRequiredConfig();
        YzhConfig yzhConfig = new YzhConfig();
        yzhConfig.setDealerId(config.getDealerId());
        yzhConfig.setBrokerId(config.getBrokerId());
        yzhConfig.setYzhAppKey(config.getAppKey());
        yzhConfig.setYzh3DesKey(config.getDes3Key());
        yzhConfig.setYzhRsaPrivateKey(config.getRsaPrivateKey());
        yzhConfig.setYzhRsaPublicKey(config.getRsaPublicKey());
        yzhConfig.setYzhUrl(config.getUrl().trim());
        yzhConfig.setSignType(parseSignType(config.getSignType()));
        yzhConfig.setIsDebug(config.getIsDebug() == null || config.getIsDebug());
        return yzhConfig;
    }

    private PaymentClient createPaymentClient() {
        return new PaymentClient(buildConfig());
    }

    private NotifyClient createNotifyClient() {
        return new NotifyClient(buildConfig());
    }

    private YzhConfig.SignType parseSignType(String signType) {
        if (!StringUtils.hasText(signType)) {
            return YzhConfig.SignType.RSA;
        }
        try {
            return YzhConfig.SignType.valueOf(signType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("云账户签名类型不支持: " + signType);
        }
    }

    private YunzhanghuConfigDto getRequiredConfig() {
        YunzhanghuConfigDto config = integrationConfigService.getYunzhanghuConfig();
        if (config == null) {
            throw new IllegalStateException("云账户配置不存在或未启用");
        }
        requireText(config.getDealerId(), "dealerId");
        requireText(config.getBrokerId(), "brokerId");
        requireText(config.getAppKey(), "appKey");
        requireText(config.getDes3Key(), "3desKey");
        requireText(config.getRsaPrivateKey(), "rsaPrivateKey");
        requireText(config.getRsaPublicKey(), "rsaPublicKey");
        requireText(config.getUrl(), "url");
        if (!StringUtils.hasText(config.getSignType())) {
            config.setSignType("rsa");
        }
        return config;
    }

    private void requireText(String value, String key) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("云账户配置缺少必填项: " + key);
        }
    }
}
