package com.yiyundao.compensation.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayFundTransUniTransferModel;
import com.alipay.api.request.AlipayFundTransUniTransferRequest;
import com.alipay.api.request.AlipayFundTransCommonQueryRequest;
import com.alipay.api.response.AlipayFundTransUniTransferResponse;
import com.alipay.api.response.AlipayFundTransCommonQueryResponse;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.internal.util.AlipayEncrypt;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayService {

    private final com.yiyundao.compensation.modules.payment.service.PaymentRecordService paymentRecordService;
    private final com.yiyundao.compensation.modules.payment.service.PaymentBatchService paymentBatchService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationService notificationService;
    private final com.yiyundao.compensation.modules.system.service.IntegrationConfigService integrationConfigService;
    private final PayrollBatchMapper payrollBatchMapper;

    private static final String DEDUP_KEY_PREFIX = "alipay:dedup:";
    private static final int BATCH_SIZE = 1000; // 批量转账最大1000笔
    private static final int DEDUP_EXPIRE_HOURS = 24; // 去重缓存24小时
    private static final String DAILY_LIMIT_KEY_PREFIX = "alipay:daily_limit:";
    private static final int ERROR_CODE_MAX_LENGTH = 50;
    private static final int ERROR_MSG_MAX_LENGTH = 500;
    private static final int NORMALIZED_ERROR_MSG_MAX_LENGTH = 200;

    /**
     * 动态创建AlipayClient
     * <p>
     * 支持两种模式：
     * 1. 公钥模式（publicKey）：使用 DefaultAlipayClient，仅基础功能
     * 2. 证书模式（cert）：使用 AlipayConfig 配置证书，支持转账等敏感操作（推荐）
     * </p>
     * <p>
     * 同时支持接口内容AES加密（独立加密机制）：
     * - 当配置了 encryptKey 和 encryptType="AES" 时，自动启用请求/响应内容加密
     * - 需在发起请求时调用 request.setNeedEncrypt(true) 开启加密
     * </p>
     */
    private AlipayClient createAlipayClient() throws IllegalStateException, AlipayApiException {
        com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto config = integrationConfigService.getAlipayConfig();
        if (config == null || config.getAppId() == null || config.getPrivateKey() == null) {
            throw new IllegalStateException("支付宝配置不完整：缺少必要的appId或privateKey");
        }

        // 构建AlipayConfig配置对象（统一支持公钥/证书模式 + AES加密）
        AlipayConfig alipayConfig = new AlipayConfig();
        alipayConfig.setServerUrl(config.getServerUrl() != null ? config.getServerUrl() : "https://openapi.alipay.com/gateway.do");
        alipayConfig.setAppId(config.getAppId());
        alipayConfig.setPrivateKey(config.getPrivateKey());
        alipayConfig.setFormat(config.getFormat() != null ? config.getFormat() : "json");
        alipayConfig.setCharset(config.getCharset() != null ? config.getCharset() : "UTF-8");
        alipayConfig.setSignType(config.getSignType() != null ? config.getSignType() : "RSA2");

        // 判断使用证书模式还是公钥模式
        boolean useCertMode = "cert".equalsIgnoreCase(config.getCertMode());

        if (useCertMode) {
            // 证书模式配置
            if (!StringUtils.hasText(config.getAppCertPath())) {
                throw new IllegalStateException("证书模式需要配置应用公钥证书路径（appCertPath）");
            }
            if (!StringUtils.hasText(config.getAlipayCertPath())) {
                throw new IllegalStateException("证书模式需要配置支付宝公钥证书路径（alipayCertPath）");
            }
            if (!StringUtils.hasText(config.getAlipayRootCertPath())) {
                throw new IllegalStateException("证书模式需要配置支付宝根证书路径（alipayRootCertPath）");
            }

            alipayConfig.setAppCertPath(config.getAppCertPath());
            alipayConfig.setAlipayPublicCertPath(config.getAlipayCertPath());
            alipayConfig.setRootCertPath(config.getAlipayRootCertPath());

            log.debug("使用证书模式创建AlipayClient: appId={}, appCert={}",
                    config.getAppId(), config.getAppCertPath());
        } else {
            // 公钥模式配置
            if (!StringUtils.hasText(config.getPublicKey())) {
                throw new IllegalStateException("公钥模式需要配置支付宝公钥（publicKey）");
            }
            alipayConfig.setAlipayPublicKey(config.getPublicKey());
            log.debug("使用公钥模式创建AlipayClient: appId={}", config.getAppId());
        }

        // 配置接口内容AES加密（独立于签名机制的额外加密层）
        if (StringUtils.hasText(config.getEncryptKey())) {
            alipayConfig.setEncryptKey(config.getEncryptKey());
            // 如果未指定encryptType，默认使用AES
            alipayConfig.setEncryptType(StringUtils.hasText(config.getEncryptType()) ? config.getEncryptType() : "AES");
            log.debug("启用支付宝接口内容AES加密: encryptType={}", alipayConfig.getEncryptType());
        }

        return new DefaultAlipayClient(alipayConfig);
    }

    /**
     * 检查是否启用了接口内容加密
     */
    private boolean isEncryptEnabled() {
        try {
            com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto config = integrationConfigService.getAlipayConfig();
            return config != null && StringUtils.hasText(config.getEncryptKey());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 单笔转账到支付宝账户
     */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = AlipayApiException.class)
    public String singleTransfer(Long paymentRecordId) throws Exception {
        PaymentRecord record = paymentRecordService.getById(paymentRecordId);
        if (record == null) {
            throw new IllegalArgumentException("支付记录不存在: " + paymentRecordId);
        }

        // 防重复支付检查
        String dedupKey = DEDUP_KEY_PREFIX + record.getId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(dedupKey))) {
            log.warn("重复支付请求: paymentRecordId={}", paymentRecordId);
            throw new IllegalStateException("重复支付请求");
        }

        // 生成商户订单号
        String outBizNo = generateOutBizNo();
        String failureCode = null;
        String failureMsg = null;

        log.info("发起支付宝单笔转账: recordId={}, outBizNo={}, amount={}",
                paymentRecordId, outBizNo, record.getAmount());

        try {
            // 检查支付宝配置是否存在且启用
            if (!integrationConfigService.isPlatformEnabled("alipay")) {
                throw new IllegalStateException("支付宝集成未启用或配置不存在");
            }

            // 读取支付宝集成配置（由管理员在系统中配置）
            com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto aliCfg = integrationConfigService.getAlipayConfig();
            if (aliCfg == null || aliCfg.getAppId() == null || aliCfg.getPrivateKey() == null) {
                throw new IllegalStateException("支付宝配置不完整：缺少必要的appId或privateKey");
            }

            log.debug("使用支付宝配置: appId={}, serverUrl={}", aliCfg.getAppId(), aliCfg.getServerUrl());

            // 设置去重标记
            redisTemplate.opsForValue().set(dedupKey, "processing", DEDUP_EXPIRE_HOURS, TimeUnit.HOURS);

            // 更新支付记录状态为处理中
            updatePaymentStatus(paymentRecordId, PaymentStatus.PROCESSING, outBizNo, null, null, null);

            // 创建支付宝客户端
            AlipayClient alipayClient = createAlipayClient();

            // 构建转账请求
            AlipayFundTransUniTransferRequest request = new AlipayFundTransUniTransferRequest();
            AlipayFundTransUniTransferModel model = new AlipayFundTransUniTransferModel();

            model.setOutBizNo(outBizNo);
            model.setTransAmount(record.getAmount().toString());
            model.setProductCode("TRANS_ACCOUNT_NO_PWD"); // 单笔转账到支付宝账户
            model.setBizScene("DIRECT_TRANSFER"); // 直接转账
            model.setOrderTitle("薪酬发放");
            model.setRemark(record.getPaymentDesc() != null ? record.getPaymentDesc() : "薪酬发放");

            // 收款方信息
            com.alipay.api.domain.Participant payeeInfo = new com.alipay.api.domain.Participant();
            String normalizedPaymentMethod = normalizePaymentMethod(record.getPaymentMethod());
            String payeeIdentity = normalizeRecipientAccount(record.getRecipientAccount(), normalizedPaymentMethod);
            if (!StringUtils.hasText(payeeIdentity)) {
                throw new IllegalArgumentException("收款账户不能为空");
            }
            payeeInfo.setIdentity(payeeIdentity);
            payeeInfo.setIdentityType(resolvePayeeIdentityType(normalizedPaymentMethod));
            if (StringUtils.hasText(record.getRecipientName())) {
                payeeInfo.setName(record.getRecipientName());
            }
            model.setPayeeInfo(payeeInfo);

            request.setBizModel(model);

            // 如果配置了接口内容加密，开启请求加密（独立于RSA签名的额外加密层）
            if (isEncryptEnabled()) {
                request.setNeedEncrypt(true);
                log.debug("单笔转账请求已开启AES内容加密: recordId={}", paymentRecordId);
            }

            // 调用支付宝API
            AlipayFundTransUniTransferResponse response = alipayClient.execute(request);

            if (response.isSuccess()) {
                String tradeNo = response.getOrderId(); // 支付宝转账单据号

                // 更新支付记录为成功
                updatePaymentStatus(paymentRecordId, PaymentStatus.SUCCESS, outBizNo,
                                  tradeNo, null, null);

                log.info("支付宝转账成功: recordId={}, outBizNo={}, tradeNo={}",
                        paymentRecordId, outBizNo, tradeNo);

                // 异步发送成功通知
                notificationService.sendPaymentSuccessNotification(record);

                return tradeNo;
            } else {
                // 转账失败
                failureCode = response.getCode();
                failureMsg = response.getMsg() + " - " + response.getSubMsg();

                log.error("支付宝转账失败: recordId={}, outBizNo={}, code={}, msg={}",
                        paymentRecordId, outBizNo, failureCode, failureMsg);

                throw new AlipayApiException(failureCode, failureMsg);
            }

        } catch (Exception e) {
            // 异常情况，删除去重标记并记录失败状态
            redisTemplate.delete(dedupKey);

            String finalFailureCode = StringUtils.hasText(failureCode) ? failureCode : "SYSTEM_ERROR";
            String finalFailureMsg = StringUtils.hasText(failureMsg) ? failureMsg : e.getMessage();
            String normalizedFailureCode = normalizeFailureCode(finalFailureCode);
            String normalizedFailureMsg = normalizeFailureMessage(finalFailureMsg);
            try {
                updatePaymentStatus(paymentRecordId, PaymentStatus.FAILED, outBizNo, null,
                        normalizedFailureCode, normalizedFailureMsg);
            } catch (Exception persistEx) {
                log.error("持久化支付失败状态异常: recordId={}, outBizNo={}", paymentRecordId, outBizNo, persistEx);
            }

            log.error("支付宝转账异常: recordId={}, outBizNo={}", paymentRecordId, outBizNo, e);
            throw new AlipayApiException(normalizedFailureCode, normalizedFailureMsg);
        }
    }

    /**
     * 批量转账处理
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void batchTransfer(String batchNo) {
        log.info("开始批量转账处理: batchNo={}", batchNo);

        PaymentBatch batch = paymentBatchService.getByBatchNo(batchNo);
        if (batch == null) {
            log.error("批次不存在: {}", batchNo);
            return;
        }

        try {
            // 更新批次状态为处理中
            paymentBatchService.updateStatus(batch.getId(), BatchStatus.PROCESSING);
            batch.setProcessStartTime(LocalDateTime.now());
            paymentBatchService.updateById(batch);
            // 使用代理调用以确保事务生效
            ((AlipayService) AopContext.currentProxy()).syncPayrollBatchStatus(batch);

            // 查询批次下的所有待处理支付记录
            List<PaymentRecord> records = paymentRecordService.getByBatchNo(batchNo, PaymentStatus.PENDING);

            if (records.isEmpty()) {
                log.warn("批次无待处理记录: {}", batchNo);
                paymentBatchService.updateStatus(batch.getId(), BatchStatus.COMPLETED);
                return;
            }

            int successCount = 0;
            int failedCount = 0;
            Exception lastException = null;

            // 按1000笔分组处理
            for (int i = 0; i < records.size(); i += BATCH_SIZE) {
                int endIndex = Math.min(i + BATCH_SIZE, records.size());
                List<PaymentRecord> subBatch = records.subList(i, endIndex);

                log.info("处理批次子集: batchNo={}, 第{}到{}笔", batchNo, i + 1, endIndex);

                for (PaymentRecord record : subBatch) {
                    try {
                        singleTransfer(record.getId());
                        successCount++;

                        // 避免支付宝API频率限制
                        Thread.sleep(100);

                    } catch (Exception e) {
                        log.error("批量转账失败: recordId={}", record.getId(), e);
                        failedCount++;
                        lastException = e;
                    }
                }
            }

            // 更新批次统计信息
            batch.setSuccessCount(successCount);
            batch.setFailedCount(failedCount);
            batch.setProcessEndTime(LocalDateTime.now());

            if (failedCount == 0) {
                batch.setStatus(BatchStatus.COMPLETED);
            } else if (successCount == 0) {
                batch.setStatus(BatchStatus.FAILED);
                // 如果全部失败，设置事务回滚
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                }
            } else {
                batch.setStatus(BatchStatus.COMPLETED); // 部分成功也认为完成
            }

            paymentBatchService.updateById(batch);
            syncPayrollBatchStatus(batch);

            log.info("批量转账完成: batchNo={}, 成功{}笔, 失败{}笔",
                    batchNo, successCount, failedCount);

            // 发送批次完成通知
            notificationService.sendBatchCompleteNotification(batch);

            // 如果有失败且错误严重，重新抛出异常
            if (successCount == 0 && lastException != null) {
                throw new RuntimeException("批量转账全部失败", lastException);
            }

        } catch (Exception e) {
            log.error("批量转账异常: batchNo={}", batchNo, e);
            batch.setStatus(BatchStatus.FAILED);
            paymentBatchService.updateStatus(batch.getId(), BatchStatus.FAILED);
            // 使用代理调用以确保事务生效
            ((AlipayService) AopContext.currentProxy()).syncPayrollBatchStatus(batch);

            // 确保事务回滚
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }

            // 重新抛出异常以触发完整回滚
            throw new RuntimeException("批量转账处理异常", e);
        }
    }

    /**
     * 同步薪资批次状态
     * <p>
     * 将支付批次的状态同步到关联的薪资批次。
     * 此方法必须在事务上下文中调用，以确保数据一致性。
     * </p>
     *
     * @param paymentBatch 支付批次
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncPayrollBatchStatus(PaymentBatch paymentBatch) {
        if (paymentBatch == null || !StringUtils.hasText(paymentBatch.getBatchNo())) {
            return;
        }
        try {
            LambdaUpdateWrapper<PayrollBatch> wrapper = new LambdaUpdateWrapper<PayrollBatch>()
                    .eq(PayrollBatch::getPaymentBatchNo, paymentBatch.getBatchNo());
            if (paymentBatch.getStatus() == BatchStatus.COMPLETED) {
                wrapper.set(PayrollBatch::getStatus, "paid");
            } else if (paymentBatch.getStatus() == BatchStatus.FAILED) {
                wrapper.set(PayrollBatch::getStatus, "pay_failed");
            } else {
                return;
            }
            payrollBatchMapper.update(null, wrapper);
            log.info("同步薪资批次状态成功: paymentBatchNo={}, status={}",
                    paymentBatch.getBatchNo(), paymentBatch.getStatus());
        } catch (Exception e) {
            log.error("同步薪资批次状态失败: paymentBatchNo={}, error={}",
                    paymentBatch.getBatchNo(), e.getMessage(), e);
            // 重新抛出异常以触发事务回滚
            throw new RuntimeException("同步薪资批次状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询转账状态
     */
    public PaymentStatus queryTransferStatus(String outBizNo) throws Exception {
        log.info("查询转账状态: outBizNo={}", outBizNo);

        try {
            // 创建支付宝客户端
            AlipayClient alipayClient = createAlipayClient();

            // 构建查询请求
            AlipayFundTransCommonQueryRequest request = new AlipayFundTransCommonQueryRequest();
            com.alipay.api.domain.AlipayFundTransCommonQueryModel model = new com.alipay.api.domain.AlipayFundTransCommonQueryModel();

            model.setOutBizNo(outBizNo);
            model.setProductCode("TRANS_ACCOUNT_NO_PWD");
            model.setBizScene("DIRECT_TRANSFER");

            request.setBizModel(model);

            // 如果配置了接口内容加密，开启请求加密
            if (isEncryptEnabled()) {
                request.setNeedEncrypt(true);
                log.debug("查询转账状态请求已开启AES内容加密: outBizNo={}", outBizNo);
            }

            // 调用支付宝查询API
            AlipayFundTransCommonQueryResponse response = alipayClient.execute(request);

            if (response.isSuccess()) {
                String status = response.getStatus();
                log.info("查询转账状态成功: outBizNo={}, status={}", outBizNo, status);

                // 映射支付宝状态到系统状态
                switch (status) {
                    case "SUCCESS":
                        return PaymentStatus.SUCCESS;
                    case "FAIL":
                        return PaymentStatus.FAILED;
                    case "DEALING":
                    case "INIT":
                        return PaymentStatus.PROCESSING;
                    case "REFUND":
                        return PaymentStatus.CANCELLED;
                    default:
                        log.warn("未知的支付宝转账状态: {}", status);
                        return PaymentStatus.PROCESSING;
                }
            } else {
                log.error("查询转账状态失败: outBizNo={}, code={}, msg={}",
                        outBizNo, response.getCode(), response.getMsg());
                throw new AlipayApiException(response.getCode(), response.getMsg());
            }

        } catch (Exception e) {
            log.error("查询转账状态异常: outBizNo={}", outBizNo, e);
            throw e;
        }
    }

    /**
     * 处理支付宝异步通知
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleNotification(String outTradeNo, String tradeNo, String tradeStatus) {
        log.info("处理支付宝通知: outTradeNo={}, tradeNo={}, status={}",
                outTradeNo, tradeNo, tradeStatus);

        PaymentRecord record = paymentRecordService.getByProviderOrderNo("alipay", outTradeNo);
        if (record == null) {
            record = paymentRecordService.getByAlipayOrderNo(outTradeNo);
        }
        if (record == null) {
            log.warn("未找到对应支付记录: outTradeNo={}", outTradeNo);
            return;
        }

        PaymentStatus newStatus;
        switch (tradeStatus) {
            case "TRADE_SUCCESS":
                newStatus = PaymentStatus.SUCCESS;
                break;
            case "TRADE_CLOSED":
            case "TRADE_FINISHED":
                newStatus = PaymentStatus.FAILED;
                break;
            default:
                log.warn("未知支付状态: {}", tradeStatus);
                return;
        }

        try {
            // 更新支付记录
            LambdaUpdateWrapper<PaymentRecord> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(PaymentRecord::getId, record.getId())
                        .set(PaymentRecord::getStatus, newStatus)
                        .set(PaymentRecord::getProviderCode, "alipay")
                        .set(PaymentRecord::getProviderOrderNo, outTradeNo)
                        .set(PaymentRecord::getProviderTradeNo, tradeNo)
                        .set(PaymentRecord::getAlipayTradeNo, tradeNo)
                        .set(PaymentRecord::getNotificationTime, LocalDateTime.now());

            paymentRecordService.update(updateWrapper);

            log.info("支付状态更新完成: recordId={}, status={}", record.getId(), newStatus);

        } catch (Exception e) {
            log.error("支付通知处理异常: outTradeNo={}, tradeNo={}", outTradeNo, tradeNo, e);

            // 确保事务回滚
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }

            throw new RuntimeException("支付通知处理失败", e);
        }
    }

    /**
     * 生成商户订单号
     */
    private String generateOutBizNo() {
        return "COMP_" + System.currentTimeMillis() + "_" +
               UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String normalizePaymentMethod(String paymentMethod) {
        if (!StringUtils.hasText(paymentMethod)) {
            return "ALIPAY";
        }
        return paymentMethod.trim().toUpperCase();
    }

    private String resolvePayeeIdentityType(String normalizedPaymentMethod) {
        return switch (normalizedPaymentMethod) {
            case "ALIPAY" -> "ALIPAY_LOGON_ID";
            case "BANK_CARD" -> "BANKCARD_ACCOUNT";
            default -> throw new IllegalArgumentException("支付宝渠道暂不支持的收款方式: " + normalizedPaymentMethod);
        };
    }

    private String normalizeRecipientAccount(String recipientAccount, String normalizedPaymentMethod) {
        if (!StringUtils.hasText(recipientAccount)) {
            return recipientAccount;
        }
        String account = recipientAccount.trim();
        if ("BANK_CARD".equals(normalizedPaymentMethod)) {
            return account.replaceAll("\\s+", "");
        }
        return account;
    }

    /**
     * 更新支付记录状态
     */
    private void updatePaymentStatus(Long recordId, PaymentStatus status, String outBizNo,
                                   String tradeNo, String errorCode, String errorMsg) {
        LambdaUpdateWrapper<PaymentRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(PaymentRecord::getId, recordId)
                    .set(PaymentRecord::getStatus, status)
                    .set(PaymentRecord::getProviderCode, "alipay")
                    .set(PaymentRecord::getProviderOrderNo, outBizNo)
                    .set(PaymentRecord::getAlipayOrderNo, outBizNo);

        if (tradeNo != null) {
            updateWrapper.set(PaymentRecord::getProviderTradeNo, tradeNo);
            updateWrapper.set(PaymentRecord::getAlipayTradeNo, tradeNo);
        }
        String safeErrorCode = trimToLength(errorCode, ERROR_CODE_MAX_LENGTH);
        String safeErrorMsg = trimToLength(errorMsg, ERROR_MSG_MAX_LENGTH);
        if (safeErrorCode != null) {
            updateWrapper.set(PaymentRecord::getErrorCode, safeErrorCode);
        } else if (status != PaymentStatus.FAILED) {
            updateWrapper.set(PaymentRecord::getErrorCode, null);
        }
        if (safeErrorMsg != null) {
            updateWrapper.set(PaymentRecord::getErrorMsg, safeErrorMsg);
        } else if (status != PaymentStatus.FAILED) {
            updateWrapper.set(PaymentRecord::getErrorMsg, null);
        }
        if (status == PaymentStatus.SUCCESS) {
            updateWrapper.set(PaymentRecord::getPaymentTime, LocalDateTime.now());
        }

        paymentRecordService.update(updateWrapper);
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private String normalizeFailureCode(String failureCode) {
        if (!StringUtils.hasText(failureCode)) {
            return "ALIPAY_TRANSFER_FAILED";
        }
        return trimToLength(failureCode, ERROR_CODE_MAX_LENGTH);
    }

    private String normalizeFailureMessage(String rawMessage) {
        if (!StringUtils.hasText(rawMessage)) {
            return "支付宝转账失败";
        }
        String message = rawMessage.trim();
        if (message.contains("RSA2签名遭遇异常")
                || message.contains("InvalidKeyException")
                || message.contains("privateKeySize")) {
            return "支付宝签名失败，请检查应用私钥格式（PKCS8）";
        }
        if (message.contains("支付宝配置不完整")) {
            return "支付宝配置不完整，请检查appId与密钥";
        }
        if (message.contains("支付宝集成未启用")) {
            return "支付宝集成未启用";
        }
        if (message.contains("收款账户不能为空")) {
            return "收款账户不能为空";
        }
        if (message.contains("重复支付请求")) {
            return "重复支付请求，请稍后重试";
        }
        int contentIndex = message.indexOf("content=");
        if (contentIndex > 0) {
            message = message.substring(0, contentIndex).trim();
        }
        String normalized = trimToLength(message, NORMALIZED_ERROR_MSG_MAX_LENGTH);
        return StringUtils.hasText(normalized) ? normalized : "支付宝转账失败";
    }

    /**
     * 验证支付宝签名
     *
     * <p>支持公钥模式和证书模式：</p>
     * <ul>
     *   <li>公钥模式：使用配置的支付宝公钥验签</li>
     *   <li>证书模式：使用支付宝公钥证书验签</li>
     * </ul>
     *
     * <p>注意：如果启用了接口内容加密，验签后还需要调用 {@link #decryptNotificationBizContent(String)}
     * 解密 biz_content 内容</p>
     */
    public boolean verifyNotification(java.util.Map<String, String> params) {
        try {
            // 获取支付宝配置
            com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto config = integrationConfigService.getAlipayConfig();
            if (config == null) {
                log.error("支付宝配置不存在");
                return false;
            }

            boolean useCertMode = "cert".equalsIgnoreCase(config.getCertMode());
            String publicKey;

            if (useCertMode) {
                // 证书模式：读取支付宝公钥证书内容
                if (!StringUtils.hasText(config.getAlipayCertPath())) {
                    log.error("证书模式需要配置支付宝公钥证书路径");
                    return false;
                }
                try {
                    publicKey = readCertContent(config.getAlipayCertPath());
                    log.debug("使用证书模式验签: certPath={}", config.getAlipayCertPath());
                } catch (java.io.IOException e) {
                    log.error("读取支付宝公钥证书失败: {}", config.getAlipayCertPath(), e);
                    return false;
                }
            } else {
                // 公钥模式：使用配置的公钥
                if (!StringUtils.hasText(config.getPublicKey())) {
                    log.error("公钥模式需要配置支付宝公钥");
                    return false;
                }
                publicKey = config.getPublicKey();
                log.debug("使用公钥模式验签");
            }

            // 使用支付宝SDK验证签名
            boolean verified = AlipaySignature.rsaCheckV1(
                params,
                publicKey,
                "UTF-8",
                "RSA2"
            );

            if (verified) {
                log.debug("支付宝通知签名验证成功");
            } else {
                log.warn("支付宝通知签名验证失败");
            }

            return verified;

        } catch (Exception e) {
            log.error("支付宝签名验证异常", e);
            return false;
        }
    }

    /**
     * 解密支付宝异步通知的biz_content内容
     *
     * <p>当应用配置了接口内容加密（AES）时，支付宝异步通知中的业务数据会被加密，
     * 需要在验签成功后调用此方法解密 biz_content。</p>
     *
     * <p>解密过程独立于签名验证：先验签，验签通过后再解密内容。</p>
     *
     * @param encryptedBizContent 加密的biz_content（从通知参数中获取）
     * @return 解密后的JSON字符串；如果未配置加密或解密失败，返回null
     */
    public String decryptNotificationBizContent(String encryptedBizContent) {
        if (!StringUtils.hasText(encryptedBizContent)) {
            return null;
        }

        try {
            // 获取支付宝配置
            com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto config =
                    integrationConfigService.getAlipayConfig();
            if (config == null || !StringUtils.hasText(config.getEncryptKey())) {
                log.debug("未配置接口内容加密密钥，跳过解密");
                return encryptedBizContent; // 返回原始内容
            }

            // 使用支付宝SDK解密
            String decryptType = StringUtils.hasText(config.getEncryptType())
                    ? config.getEncryptType()
                    : "AES";

            String decryptedContent = AlipayEncrypt.decryptContent(
                    encryptedBizContent,
                    decryptType,
                    config.getEncryptKey(),
                    config.getCharset() != null ? config.getCharset() : "UTF-8"
            );

            log.debug("支付宝通知biz_content解密成功");
            return decryptedContent;

        } catch (AlipayApiException e) {
            log.error("支付宝通知biz_content解密失败: {}", e.getErrMsg(), e);
            return null;
        } catch (Exception e) {
            log.error("解密支付宝通知内容异常", e);
            return null;
        }
    }

    /**
     * 读取证书文件内容
     *
     * @param certPath 证书文件路径
     * @return 证书内容字符串
     * @throws java.io.IOException 读取失败时抛出
     */
    private String readCertContent(String certPath) throws java.io.IOException {
        java.nio.file.Path path = java.nio.file.Paths.get(certPath);
        if (!java.nio.file.Files.exists(path)) {
            throw new java.io.IOException("证书文件不存在: " + certPath);
        }
        return new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 获取支付限额配置
     */
    public BigDecimal getDailyLimit() {
        try {
            // 从系统配置中读取每日支付限额
            String limitStr = integrationConfigService.getConfigValue("alipay", "daily_limit", "10000.00");
            return new BigDecimal(limitStr);
        } catch (Exception e) {
            log.warn("读取支付限额配置失败，使用默认值: {}", e.getMessage());
            return new BigDecimal("10000.00");
        }
    }

    /**
     * 检查支付限额
     */
    public boolean checkDailyLimit(Long employeeId, BigDecimal amount) {
        try {
            BigDecimal dailyLimit = getDailyLimit();

            // 检查单笔金额是否超过限额
            if (amount.compareTo(dailyLimit) > 0) {
                log.warn("单笔支付金额超过限额: employeeId={}, amount={}, limit={}",
                        employeeId, amount, dailyLimit);
                return false;
            }

            // 获取今日已支付总额
            String today = java.time.LocalDate.now().toString();
            String dailyLimitKey = DAILY_LIMIT_KEY_PREFIX + employeeId + ":" + today;

            String totalAmountStr = (String) redisTemplate.opsForValue().get(dailyLimitKey);
            BigDecimal totalAmount = totalAmountStr != null ? new BigDecimal(totalAmountStr) : BigDecimal.ZERO;

            // 检查今日累计金额 + 本次金额是否超过限额
            BigDecimal newTotal = totalAmount.add(amount);
            if (newTotal.compareTo(dailyLimit) > 0) {
                log.warn("今日累计支付金额超过限额: employeeId={}, totalAmount={}, newAmount={}, limit={}",
                        employeeId, totalAmount, amount, dailyLimit);
                return false;
            }

            // 更新今日累计金额（设置过期时间为明天凌晨）
            redisTemplate.opsForValue().set(dailyLimitKey, newTotal.toString());

            // 计算到明天凌晨的秒数
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime tomorrow = now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            long secondsUntilTomorrow = java.time.Duration.between(now, tomorrow).getSeconds();
            redisTemplate.expire(dailyLimitKey, secondsUntilTomorrow, TimeUnit.SECONDS);

            log.debug("支付限额检查通过: employeeId={}, amount={}, todayTotal={}, limit={}",
                    employeeId, amount, newTotal, dailyLimit);
            return true;

        } catch (Exception e) {
            log.error("检查支付限额异常: employeeId={}, amount={}", employeeId, amount, e);
            // 异常情况下，为了安全起见，拒绝支付
            return false;
        }
    }

    /**
     * 验证支付宝配置连接
     */
    public boolean checkAlipayConnection() {
        try {
            // 检查支付宝配置是否启用
            if (!integrationConfigService.isPlatformEnabled("alipay")) {
                log.warn("支付宝集成未启用");
                return false;
            }

            // 检查配置完整性
            com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto config = integrationConfigService.getAlipayConfig();
            if (config == null) {
                log.warn("支付宝配置不存在");
                return false;
            }

            // 检查必需配置项
            if (config.getAppId() == null || config.getAppId().trim().isEmpty()) {
                log.warn("支付宝应用ID未配置");
                return false;
            }

            if (config.getPrivateKey() == null || config.getPrivateKey().trim().isEmpty()) {
                log.warn("支付宝应用私钥未配置");
                return false;
            }

            if (config.getPublicKey() == null || config.getPublicKey().trim().isEmpty()) {
                log.warn("支付宝平台公钥未配置");
                return false;
            }

            // 调用支付宝API验证连接（使用查询接口测试）
            try {
                AlipayClient alipayClient = createAlipayClient();

                // 使用一个不存在的订单号查询，如果返回特定错误码说明连接正常
                AlipayFundTransCommonQueryRequest request = new AlipayFundTransCommonQueryRequest();
                com.alipay.api.domain.AlipayFundTransCommonQueryModel model = new com.alipay.api.domain.AlipayFundTransCommonQueryModel();

                model.setOutBizNo("CONNECTION_TEST_" + System.currentTimeMillis());
                model.setProductCode("TRANS_ACCOUNT_NO_PWD");
                model.setBizScene("DIRECT_TRANSFER");

                request.setBizModel(model);

                AlipayFundTransCommonQueryResponse response = alipayClient.execute(request);

                // 如果能收到响应（即使是错误响应），说明连接正常
                if (response != null) {
                    log.info("支付宝连接验证成功: appId={}, responseCode={}", config.getAppId(), response.getCode());
                    return true;
                } else {
                    log.warn("支付宝连接验证失败: 响应为空");
                    return false;
                }

            } catch (AlipayApiException e) {
                // 某些API异常也说明连接是通的，只是业务逻辑问题
                if (e.getErrCode() != null) {
                    log.info("支付宝连接验证成功（业务异常）: appId={}, errCode={}", config.getAppId(), e.getErrCode());
                    return true;
                } else {
                    log.error("支付宝连接验证失败: {}", e.getMessage());
                    return false;
                }
            }

        } catch (Exception e) {
            log.error("支付宝连接检查异常", e);
            return false;
        }
    }
}
