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
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yiyundao.compensation.common.utils.AlipayKeyFormatValidator;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.service.PayrollSettlementIntegrityService;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.modules.payment.support.PaymentCallbackLogSanitizer;
import com.yiyundao.compensation.modules.payment.support.PaymentRecordStatusTransitions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private PayrollSettlementIntegrityService payrollSettlementIntegrityService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setPayrollSettlementIntegrityService(
            PayrollSettlementIntegrityService payrollSettlementIntegrityService) {
        this.payrollSettlementIntegrityService = payrollSettlementIntegrityService;
    }

    private static final String DEDUP_KEY_PREFIX = "alipay:dedup:";
    private static final int BATCH_SIZE = 1000; // 批量转账最大1000笔
    private static final int DEDUP_EXPIRE_HOURS = 24; // 去重缓存24小时
    private static final String DAILY_LIMIT_KEY_PREFIX = "alipay:daily_limit:";
    public static final String RESULT_UNKNOWN_ERROR_CODE = "ALIPAY_RESULT_UNKNOWN";
    private static final int ERROR_CODE_MAX_LENGTH = 50;
    private static final int ERROR_MSG_MAX_LENGTH = 500;
    private static final int NORMALIZED_ERROR_MSG_MAX_LENGTH = 200;

    private String maskOrderNo(String orderNo) {
        return PaymentCallbackLogSanitizer.sanitizeField("out_biz_no", orderNo);
    }

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
        if (config == null || !StringUtils.hasText(config.getAppId()) || !StringUtils.hasText(config.getPrivateKey())) {
            throw new IllegalStateException("支付宝配置不完整：缺少必要的appId或privateKey");
        }
        String privateKey = AlipayKeyFormatValidator.normalizePkcs8PrivateKey(config.getPrivateKey());

        // 构建AlipayConfig配置对象（统一支持公钥/证书模式 + AES加密）
        AlipayConfig alipayConfig = new AlipayConfig();
        alipayConfig.setServerUrl(config.getServerUrl() != null ? config.getServerUrl() : "https://openapi.alipay.com/gateway.do");
        alipayConfig.setAppId(config.getAppId().trim());
        alipayConfig.setPrivateKey(privateKey);
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
        String existingOrderNo = resolveExistingOrderNo(record);
        boolean submissionAttempted = false;
        boolean responseReceived = false;
        String failureCode = null;
        String failureMsg = null;

        log.info("发起支付宝单笔转账: recordId={}, outBizNo={}, amount={}",
                paymentRecordId, maskOrderNo(outBizNo), record.getAmount());

        try {
            if (StringUtils.hasText(existingOrderNo)) {
                throw new IllegalStateException("支付记录已有支付宝渠道订单，请先完成渠道查单");
            }

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
            if (!updatePaymentStatus(paymentRecordId, PaymentStatus.PROCESSING, outBizNo, null, null, null)) {
                redisTemplate.delete(dedupKey);
                throw new IllegalStateException("支付记录状态已变更，禁止重复发起转账");
            }

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
            submissionAttempted = true;
            AlipayFundTransUniTransferResponse response = alipayClient.execute(request);
            responseReceived = response != null;

            if (response != null && response.isSuccess()) {
                String tradeNo = response.getOrderId(); // 支付宝转账单据号

                // 更新支付记录为成功
                updatePaymentStatus(paymentRecordId, PaymentStatus.SUCCESS, outBizNo,
                                  tradeNo, null, null);

                log.info("支付宝转账成功: recordId={}, outBizNo={}, tradeNo={}",
                        paymentRecordId, maskOrderNo(outBizNo),
                        PaymentCallbackLogSanitizer.sanitizeField("trade_no", tradeNo));

                safeNotifyPaymentSuccess(record);

                return tradeNo;
            } else {
                // 收到明确的支付宝失败响应时才标记失败；无响应属于结果未知，必须保留订单号等待查单。
                failureCode = response == null ? "ALIPAY_EMPTY_RESPONSE" : response.getCode();
                failureMsg = response == null
                        ? "支付宝未返回转账结果"
                        : response.getMsg() + " - " + response.getSubMsg();

                log.error("支付宝转账失败: recordId={}, outBizNo={}, code={}, msg={}",
                        paymentRecordId, maskOrderNo(outBizNo), failureCode, failureMsg);

                throw new AlipayApiException(failureCode, failureMsg);
            }

        } catch (Exception e) {
            // 异常情况，删除去重标记并记录最终状态
            redisTemplate.delete(dedupKey);

            String finalFailureCode = StringUtils.hasText(failureCode) ? failureCode : "SYSTEM_ERROR";
            String finalFailureMsg = StringUtils.hasText(failureMsg) ? failureMsg : e.getMessage();
            boolean resultUnknown = submissionAttempted
                    && !responseReceived
                    && !isLocalAlipayConfigurationError(e);
            if (resultUnknown) {
                finalFailureCode = RESULT_UNKNOWN_ERROR_CODE;
                finalFailureMsg = "支付宝转账结果未知，请通过查单确认后再处理";
            }
            String normalizedFailureCode = normalizeFailureCode(finalFailureCode);
            String normalizedFailureMsg = normalizeFailureMessage(finalFailureMsg);
            String failureOrderNo = submissionAttempted ? outBizNo : existingOrderNo;
            try {
                updatePaymentStatus(paymentRecordId, resultUnknown ? PaymentStatus.PROCESSING : PaymentStatus.FAILED,
                        failureOrderNo, null,
                        normalizedFailureCode, normalizedFailureMsg);
            } catch (Exception persistEx) {
                log.error("持久化支付失败状态异常: recordId={}, outBizNo={}",
                        paymentRecordId, maskOrderNo(outBizNo), persistEx);
            }

            log.error("支付宝转账异常: recordId={}, outBizNo={}", paymentRecordId, maskOrderNo(outBizNo), e);
            throw new AlipayApiException(normalizedFailureCode, normalizedFailureMsg);
        }
    }

    /**
     * 批量转账处理
     */
    @Async
    public void batchTransfer(String batchNo) {
        log.info("开始批量转账处理: batchNo={}", batchNo);

        PaymentBatch batch = paymentBatchService.getByBatchNo(batchNo);
        if (batch == null) {
            log.error("批次不存在: {}", batchNo);
            return;
        }

        try {
            // 更新批次状态为处理中
            LocalDateTime processStartTime = LocalDateTime.now();
            if (!claimBatchForTransfer(batch, processStartTime)) {
                log.info("支付宝批量转账批次已被其他任务领取或状态已变更，跳过: batchNo={}, status={}",
                        batchNo, batch.getStatus());
                return;
            }
            batch.setStatus(BatchStatus.PROCESSING);
            batch.setPaymentStatus(PaymentBatchProcessStatus.PROCESSING);
            batch.setProcessStartTime(processStartTime);
            batch.setProcessEndTime(null);

            // 查询批次下的所有待处理支付记录
            List<PaymentRecord> records = paymentRecordService.getByBatchNo(batchNo, PaymentStatus.PENDING);

            if (records.isEmpty()) {
                log.warn("批次无待处理记录: {}", batchNo);
                applyTerminalBatchResult(batch, paymentRecordService.getByBatchNo(batchNo, null));
                paymentBatchService.updateTerminalState(batch);
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

            applyTerminalBatchResult(batch, successCount, failedCount);
            paymentBatchService.updateTerminalState(batch);

            log.info("批量转账完成: batchNo={}, 成功{}笔, 失败{}笔",
                    batchNo, successCount, failedCount);

            // 发送批次完成通知
            notificationService.sendBatchCompleteNotification(batch);
            if (successCount == 0 && lastException != null) {
                log.warn("批量转账全部失败，已持久化失败状态: batchNo={}, lastError={}",
                        batchNo, lastException.getMessage());
            }

        } catch (Exception e) {
            log.error("批量转账异常: batchNo={}", batchNo, e);
            batch.setStatus(BatchStatus.FAILED);
            batch.setPaymentStatus(PaymentBatchProcessStatus.FAILED);
            paymentBatchService.updateTerminalState(batch);
        }
    }

    private boolean claimBatchForTransfer(PaymentBatch batch, LocalDateTime processStartTime) {
        if (batch == null || batch.getId() == null) {
            return false;
        }
        return paymentBatchService.update(new UpdateWrapper<PaymentBatch>()
                .eq("id", batch.getId())
                .in("status", BatchStatus.SUBMITTED.getCode(), BatchStatus.APPROVED.getCode())
                .set("status", BatchStatus.PROCESSING.getCode())
                .set("payment_status", PaymentBatchProcessStatus.PROCESSING.getCode())
                .set("process_start_time", processStartTime)
                .set("process_end_time", null));
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
            boolean partialSuccess = paymentBatch.getPaymentStatus() == PaymentBatchProcessStatus.PARTIAL_SUCCESS
                    || (paymentBatch.getSuccessCount() != null && paymentBatch.getSuccessCount() > 0
                    && paymentBatch.getFailedCount() != null && paymentBatch.getFailedCount() > 0);
            UpdateWrapper<PayrollBatch> wrapper = new UpdateWrapper<PayrollBatch>()
                    .eq("payment_batch_no", paymentBatch.getBatchNo())
                    .in("status", PayrollBatchStatus.PAY_PROCESSING.getCode(), PayrollBatchStatus.PAY_FAILED.getCode());
            if (paymentBatch.getStatus() == BatchStatus.COMPLETED) {
                wrapper.set("status",
                        partialSuccess ? PayrollBatchStatus.PAY_FAILED.getCode() : PayrollBatchStatus.PAID.getCode());
            } else if (paymentBatch.getStatus() == BatchStatus.FAILED) {
                wrapper.set("status", PayrollBatchStatus.PAY_FAILED.getCode());
            } else {
                return;
            }
            PayrollBatchStatus targetStatus = partialSuccess
                    ? PayrollBatchStatus.PAY_FAILED
                    : PayrollBatchStatus.PAID;
            if (targetStatus == PayrollBatchStatus.PAID && payrollSettlementIntegrityService != null) {
                payrollSettlementIntegrityService.finalizeByPaymentBatchNo(
                        paymentBatch.getBatchNo(),
                        targetStatus,
                        PaymentBatchProcessStatus.SUCCESS.getCode());
            } else {
                payrollBatchMapper.update(null, wrapper);
            }
            log.info("同步薪资批次状态成功: paymentBatchNo={}, status={}",
                    paymentBatch.getBatchNo(), paymentBatch.getStatus());
        } catch (Exception e) {
            log.error("同步薪资批次状态失败: paymentBatchNo={}, error={}",
                    paymentBatch.getBatchNo(), e.getMessage(), e);
            // 重新抛出异常以触发事务回滚
            throw new RuntimeException("同步薪资批次状态失败: " + e.getMessage(), e);
        }
    }

    private void applyTerminalBatchResult(PaymentBatch batch, List<PaymentRecord> allRecords) {
        if (allRecords == null || allRecords.isEmpty()) {
            applyTerminalBatchResult(batch, 0, 0);
            batch.setStatus(BatchStatus.FAILED);
            batch.setPaymentStatus(PaymentBatchProcessStatus.FAILED);
            return;
        }
        int successCount = 0;
        int failedCount = 0;
        for (PaymentRecord record : allRecords) {
            PaymentStatus status = record.getStatus();
            if (status == PaymentStatus.SUCCESS) {
                successCount++;
            } else if (status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED) {
                failedCount++;
            }
        }
        applyTerminalBatchResult(batch, successCount, failedCount);
    }

    private void applyTerminalBatchResult(PaymentBatch batch, int successCount, int failedCount) {
        batch.setSuccessCount(successCount);
        batch.setFailedCount(failedCount);
        batch.setProcessEndTime(LocalDateTime.now());
        if (failedCount == 0 && successCount > 0) {
            batch.setStatus(BatchStatus.COMPLETED);
            batch.setPaymentStatus(PaymentBatchProcessStatus.SUCCESS);
        } else if (successCount == 0) {
            batch.setStatus(BatchStatus.FAILED);
            batch.setPaymentStatus(PaymentBatchProcessStatus.FAILED);
        } else {
            batch.setStatus(BatchStatus.COMPLETED);
            batch.setPaymentStatus(PaymentBatchProcessStatus.PARTIAL_SUCCESS);
        }
    }

    /**
     * 查询转账状态
     */
    public PaymentStatus queryTransferStatus(String outBizNo) throws Exception {
        log.info("查询转账状态: outBizNo={}", maskOrderNo(outBizNo));

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
                log.debug("查询转账状态请求已开启AES内容加密: outBizNo={}", maskOrderNo(outBizNo));
            }

            // 调用支付宝查询API
            AlipayFundTransCommonQueryResponse response = alipayClient.execute(request);

            if (response.isSuccess()) {
                String status = response.getStatus();
                log.info("查询转账状态成功: outBizNo={}, status={}", maskOrderNo(outBizNo), status);

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
                        maskOrderNo(outBizNo), response.getCode(), response.getMsg());
                throw new AlipayApiException(response.getCode(), response.getMsg());
            }

        } catch (Exception e) {
            if (isLocalAlipayConfigurationError(e)) {
                log.warn("查询转账状态失败: outBizNo={}, msg={}",
                        maskOrderNo(outBizNo), normalizeFailureMessage(e.getMessage()));
            } else {
                log.error("查询转账状态异常: outBizNo={}", maskOrderNo(outBizNo), e);
            }
            throw e;
        }
    }

    /**
     * 处理支付宝异步通知
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleNotification(String outTradeNo, String tradeNo, String tradeStatus) {
        log.info("处理支付宝通知: outTradeNo={}, tradeNo={}, status={}",
                PaymentCallbackLogSanitizer.sanitizeField("out_trade_no", outTradeNo),
                PaymentCallbackLogSanitizer.sanitizeField("trade_no", tradeNo),
                tradeStatus);

        PaymentRecord record = paymentRecordService.getByProviderOrderNo("alipay", outTradeNo);
        if (record == null) {
            record = paymentRecordService.getByAlipayOrderNo(outTradeNo);
        }
        if (record == null) {
            log.warn("未找到对应支付记录: outTradeNo={}",
                    PaymentCallbackLogSanitizer.sanitizeField("out_trade_no", outTradeNo));
            throw new IllegalStateException("支付宝回调未匹配到支付记录");
        }

        PaymentStatus newStatus;
        switch (tradeStatus) {
            case "TRADE_SUCCESS":
            case "TRADE_FINISHED":
                newStatus = PaymentStatus.SUCCESS;
                break;
            case "TRADE_CLOSED":
                newStatus = PaymentStatus.FAILED;
                break;
            default:
                log.warn("未知支付状态: {}", tradeStatus);
                return;
        }

        try {
            PaymentStatus previousStatus = record.getStatus();
            // 更新支付记录
            UpdateWrapper<PaymentRecord> updateWrapper = new UpdateWrapper<PaymentRecord>()
                    .eq("id", record.getId())
                    .set("status", newStatus.getCode())
                    .set("provider_code", "alipay")
                    .set("provider_order_no", outTradeNo)
                    .set("provider_trade_no", tradeNo)
                    .set("alipay_trade_no", tradeNo);
            if (newStatus == PaymentStatus.SUCCESS) {
                updateWrapper.set("payment_time", LocalDateTime.now())
                        .set("error_code", null)
                        .set("error_msg", null);
            } else if (newStatus == PaymentStatus.FAILED) {
                updateWrapper.set("error_code", "ALIPAY_TRADE_CLOSED")
                        .set("error_msg", "支付宝回调确认支付失败");
            }
            PaymentRecordStatusTransitions.applyAllowedStatusGuard(updateWrapper, newStatus);

            boolean updated = paymentRecordService.update(updateWrapper);
            if (!updated) {
                log.warn("支付宝通知未更新支付记录，可能为迟到回调或状态已终态: recordId={}, currentStatus={}, targetStatus={}",
                        record.getId(), record.getStatus(), newStatus);
                return;
            }

            if (newStatus == PaymentStatus.SUCCESS && previousStatus != PaymentStatus.SUCCESS) {
                record.setStatus(PaymentStatus.SUCCESS);
                record.setPaymentTime(LocalDateTime.now());
                record.setProviderOrderNo(outTradeNo);
                record.setProviderTradeNo(tradeNo);
                safeNotifyPaymentSuccess(record);
            } else if (newStatus == PaymentStatus.FAILED
                    && previousStatus != PaymentStatus.FAILED
                    && previousStatus != PaymentStatus.CANCELLED) {
                record.setStatus(PaymentStatus.FAILED);
                record.setProviderOrderNo(outTradeNo);
                record.setProviderTradeNo(tradeNo);
                record.setErrorMsg("支付宝回调确认支付失败");
                safeNotifyPaymentFailed(record);
            }

            refreshBatchStatusAfterNotification(record);
            log.info("支付状态更新完成: recordId={}, status={}", record.getId(), newStatus);

        } catch (Exception e) {
            log.error("支付通知处理异常: outTradeNo={}, tradeNo={}",
                    PaymentCallbackLogSanitizer.sanitizeField("out_trade_no", outTradeNo),
                    PaymentCallbackLogSanitizer.sanitizeField("trade_no", tradeNo), e);

            // 确保事务回滚
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }

            throw new RuntimeException("支付通知处理失败", e);
        }
    }

    private void refreshBatchStatusAfterNotification(PaymentRecord record) {
        if (record == null || !StringUtils.hasText(record.getBatchNo())) {
            return;
        }
        PaymentBatch batch = paymentBatchService.getByBatchNo(record.getBatchNo());
        if (batch == null) {
            return;
        }
        BatchStatus previousStatus = batch.getStatus();

        List<PaymentRecord> allRecords = paymentRecordService.getByBatchNo(record.getBatchNo(), null);
        int successCount = 0;
        int failedCount = 0;
        int processingCount = 0;
        for (PaymentRecord batchRecord : allRecords) {
            PaymentStatus status = batchRecord.getStatus();
            if (status == PaymentStatus.SUCCESS) {
                successCount++;
            } else if (status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED) {
                failedCount++;
            } else {
                processingCount++;
            }
        }

        BatchStatus targetStatus;
        PaymentBatchProcessStatus targetPaymentStatus;
        LocalDateTime targetProcessEndTime;
        if (processingCount > 0) {
            targetStatus = BatchStatus.PROCESSING;
            targetPaymentStatus = PaymentBatchProcessStatus.PROCESSING;
            targetProcessEndTime = null;
        } else if (failedCount == 0 && successCount > 0) {
            targetStatus = BatchStatus.COMPLETED;
            targetPaymentStatus = PaymentBatchProcessStatus.SUCCESS;
            targetProcessEndTime = LocalDateTime.now();
        } else if (successCount == 0) {
            targetStatus = BatchStatus.FAILED;
            targetPaymentStatus = PaymentBatchProcessStatus.FAILED;
            targetProcessEndTime = LocalDateTime.now();
        } else {
            targetStatus = BatchStatus.COMPLETED;
            targetPaymentStatus = PaymentBatchProcessStatus.PARTIAL_SUCCESS;
            targetProcessEndTime = LocalDateTime.now();
        }

        if (isTerminalBatchStatus(previousStatus) && !isTerminalBatchStatus(targetStatus)) {
            log.warn("忽略支付宝回调导致的支付批次终态回退: batchNo={}, previousStatus={}, targetStatus={}",
                    batch.getBatchNo(), previousStatus, targetStatus);
            return;
        }

        batch.setSuccessCount(successCount);
        batch.setFailedCount(failedCount);
        batch.setStatus(targetStatus);
        batch.setPaymentStatus(targetPaymentStatus);
        batch.setProcessEndTime(targetProcessEndTime);

        paymentBatchService.updateTerminalState(batch);
        if (batch.getStatus() == BatchStatus.COMPLETED || batch.getStatus() == BatchStatus.FAILED) {
            if (previousStatus != batch.getStatus()) {
                try {
                    notificationService.sendBatchCompleteNotification(batch);
                } catch (Exception ex) {
                    log.warn("支付宝回调后的批次完成通知发送失败: batchNo={}, msg={}",
                            batch.getBatchNo(), ex.getMessage());
                }
            }
        }
    }

    private boolean isTerminalBatchStatus(BatchStatus status) {
        return status == BatchStatus.COMPLETED || status == BatchStatus.FAILED;
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
    private boolean updatePaymentStatus(Long recordId, PaymentStatus status, String outBizNo,
                                        String tradeNo, String errorCode, String errorMsg) {
        UpdateWrapper<PaymentRecord> updateWrapper = new UpdateWrapper<PaymentRecord>()
                .eq("id", recordId)
                .set("status", status.getCode())
                .set("provider_code", "alipay")
                .set("provider_order_no", outBizNo)
                .set("alipay_order_no", outBizNo);

        if (tradeNo != null) {
            updateWrapper.set("provider_trade_no", tradeNo);
            updateWrapper.set("alipay_trade_no", tradeNo);
        }
        String safeErrorCode = trimToLength(errorCode, ERROR_CODE_MAX_LENGTH);
        String safeErrorMsg = trimToLength(errorMsg, ERROR_MSG_MAX_LENGTH);
        if (safeErrorCode != null) {
            updateWrapper.set("error_code", safeErrorCode);
        } else if (status != PaymentStatus.FAILED) {
            updateWrapper.set("error_code", null);
        }
        if (safeErrorMsg != null) {
            updateWrapper.set("error_msg", safeErrorMsg);
        } else if (status != PaymentStatus.FAILED) {
            updateWrapper.set("error_msg", null);
        }
        if (status == PaymentStatus.SUCCESS) {
            updateWrapper.set("payment_time", LocalDateTime.now());
        }

        PaymentRecordStatusTransitions.applyAllowedStatusGuard(updateWrapper, status);
        return paymentRecordService.update(updateWrapper);
    }

    private String resolveExistingOrderNo(PaymentRecord record) {
        if (record == null) {
            return null;
        }
        if (StringUtils.hasText(record.getProviderOrderNo())) {
            return record.getProviderOrderNo();
        }
        return StringUtils.hasText(record.getAlipayOrderNo()) ? record.getAlipayOrderNo() : null;
    }

    private void safeNotifyPaymentSuccess(PaymentRecord record) {
        if (record == null || record.getId() == null) {
            return;
        }
        boolean claimed = paymentRecordService.update(new UpdateWrapper<PaymentRecord>()
                .eq("id", record.getId())
                .eq("status", PaymentStatus.SUCCESS.getCode())
                .isNull("notification_time")
                .set("notification_time", LocalDateTime.now()));
        if (!claimed) {
            return;
        }
        try {
            record.setStatus(PaymentStatus.SUCCESS);
            if (record.getPaymentTime() == null) {
                record.setPaymentTime(LocalDateTime.now());
            }
            notificationService.sendPaymentSuccessNotification(record);
        } catch (Exception notifyEx) {
            log.warn("支付宝转账成功通知发送失败: recordId={}, msg={}", record.getId(), notifyEx.getMessage());
        }
    }

    private void safeNotifyPaymentFailed(PaymentRecord record) {
        if (record == null || record.getId() == null) {
            return;
        }
        boolean claimed = paymentRecordService.update(new UpdateWrapper<PaymentRecord>()
                .eq("id", record.getId())
                .eq("status", PaymentStatus.FAILED.getCode())
                .isNull("notification_time")
                .set("notification_time", LocalDateTime.now()));
        if (!claimed) {
            return;
        }
        try {
            record.setStatus(PaymentStatus.FAILED);
            notificationService.sendPaymentFailedNotification(record);
        } catch (Exception notifyEx) {
            log.warn("支付宝转账失败通知发送失败: recordId={}, msg={}", record.getId(), notifyEx.getMessage());
        }
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
                || message.contains("privateKeySize")
                || message.contains("支付宝应用私钥格式错误")) {
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
            if (params == null || params.isEmpty()) {
                return false;
            }
            // 获取支付宝配置
            com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto config = integrationConfigService.getAlipayConfig();
            if (config == null) {
                log.error("支付宝配置不存在");
                return false;
            }

            boolean useCertMode = "cert".equalsIgnoreCase(config.getCertMode());
            String charset = resolveAlipayCallbackCharset(params, config);
            String signType = resolveAlipayCallbackSignType(params, config);
            Map<String, String> paramsForVerify = new LinkedHashMap<>(params);

            boolean verified;
            if (useCertMode) {
                if (!StringUtils.hasText(config.getAlipayCertPath())) {
                    log.error("证书模式需要配置支付宝公钥证书路径");
                    return false;
                }
                verified = AlipaySignature.rsaCertCheckV1(
                        paramsForVerify,
                        config.getAlipayCertPath(),
                        charset,
                        signType
                );
                log.debug("使用证书模式验签: certPath={}", config.getAlipayCertPath());
            } else {
                // 公钥模式：使用配置的公钥
                if (!StringUtils.hasText(config.getPublicKey())) {
                    log.error("公钥模式需要配置支付宝公钥");
                    return false;
                }
                verified = AlipaySignature.rsaCheckV1(
                    paramsForVerify,
                    config.getPublicKey(),
                    charset,
                    signType
                );
                log.debug("使用公钥模式验签");
            }

            if (verified) {
                if (!matchesCallbackAppId(params, config)) {
                    log.warn("支付宝通知应用ID不匹配，拒绝处理");
                    return false;
                }
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

    private boolean matchesCallbackAppId(java.util.Map<String, String> params,
                                         com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto config) {
        String callbackAppId = params != null ? params.get("app_id") : null;
        if (!StringUtils.hasText(callbackAppId)) {
            return true;
        }
        if (config == null || !StringUtils.hasText(config.getAppId())) {
            log.warn("支付宝通知包含app_id但系统未配置应用ID，拒绝处理: callbackAppId={}",
                    PaymentCallbackLogSanitizer.sanitizeField("app_id", callbackAppId));
            return false;
        }
        boolean matched = config.getAppId().trim().equals(callbackAppId.trim());
        if (!matched) {
            log.warn("支付宝通知app_id与系统配置不匹配: callbackAppId={}, configuredAppId={}",
                    PaymentCallbackLogSanitizer.sanitizeField("app_id", callbackAppId),
                    PaymentCallbackLogSanitizer.sanitizeField("app_id", config.getAppId()));
        }
        return matched;
    }

    private String resolveAlipayCallbackCharset(java.util.Map<String, String> params,
                                                com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto config) {
        String charset = params != null ? params.get("charset") : null;
        if (!StringUtils.hasText(charset) && config != null) {
            charset = config.getCharset();
        }
        return StringUtils.hasText(charset) ? charset.trim() : "UTF-8";
    }

    private String resolveAlipayCallbackSignType(java.util.Map<String, String> params,
                                                 com.yiyundao.compensation.interfaces.dto.config.AlipayConfigDto config) {
        String signType = params != null ? params.get("sign_type") : null;
        if (!StringUtils.hasText(signType) && config != null) {
            signType = config.getSignType();
        }
        return StringUtils.hasText(signType) ? signType.trim() : "RSA2";
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
            if (!StringUtils.hasText(config.getAppId())) {
                log.warn("支付宝应用ID未配置");
                return false;
            }

            try {
                AlipayKeyFormatValidator.normalizePkcs8PrivateKey(config.getPrivateKey());
            } catch (IllegalStateException ex) {
                log.warn(ex.getMessage());
                return false;
            }

            boolean useCertMode = "cert".equalsIgnoreCase(config.getCertMode());
            if (useCertMode) {
                if (!StringUtils.hasText(config.getAppCertPath())
                        || !StringUtils.hasText(config.getAlipayCertPath())
                        || !StringUtils.hasText(config.getAlipayRootCertPath())) {
                    log.warn("支付宝证书模式证书路径未配置完整");
                    return false;
                }
            } else {
                if (!StringUtils.hasText(config.getPublicKey())) {
                    log.warn("支付宝平台公钥未配置");
                    return false;
                }
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

    private boolean isLocalAlipayConfigurationError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (StringUtils.hasText(message)
                    && (message.contains("RSA2签名遭遇异常")
                    || message.contains("InvalidKeyException")
                    || message.contains("privateKeySize")
                    || message.contains("支付宝配置不完整")
                    || message.contains("支付宝应用私钥格式错误")
                    || message.contains("支付宝集成未启用")
                    || message.contains("公钥模式需要配置")
                    || message.contains("证书模式需要配置"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
