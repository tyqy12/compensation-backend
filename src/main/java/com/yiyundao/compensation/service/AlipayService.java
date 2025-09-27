package com.yiyundao.compensation.service;

// 暂时注释支付宝API相关导入，等SDK下载完成后启用
/*
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayFundTransUniTransferModel;
import com.alipay.api.request.AlipayFundTransUniTransferRequest;
import com.alipay.api.request.AlipayFundTransCommonQueryRequest;
import com.alipay.api.response.AlipayFundTransUniTransferResponse;
import com.alipay.api.response.AlipayFundTransCommonQueryResponse;
*/
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.enums.BatchStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayService {

    // 暂时注释AlipayClient，等SDK下载完成后启用
    // private final AlipayClient alipayClient;
    private final com.yiyundao.compensation.modules.payment.service.PaymentRecordService paymentRecordService;
    private final com.yiyundao.compensation.modules.payment.service.PaymentBatchService paymentBatchService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationService notificationService;

    private static final String DEDUP_KEY_PREFIX = "alipay:dedup:";
    private static final int BATCH_SIZE = 1000; // 批量转账最大1000笔
    private static final int DEDUP_EXPIRE_HOURS = 24; // 去重缓存24小时

    /**
     * 单笔转账到支付宝账户 (暂时模拟实现)
     */
    @Transactional(rollbackFor = Exception.class)
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

        log.info("发起支付宝单笔转账(模拟): recordId={}, outBizNo={}, amount={}",
                paymentRecordId, outBizNo, record.getAmount());

        try {
            // 设置去重标记
            redisTemplate.opsForValue().set(dedupKey, "processing", DEDUP_EXPIRE_HOURS, TimeUnit.HOURS);

            // 更新支付记录状态为处理中
            updatePaymentStatus(paymentRecordId, PaymentStatus.PROCESSING, outBizNo, null, null, null);

            // TODO: 这里暂时模拟成功，实际需要调用支付宝API
            Thread.sleep(1000); // 模拟API调用时间

            String mockTradeNo = "MOCK_" + System.currentTimeMillis();

            // 模拟转账成功，更新记录
            updatePaymentStatus(paymentRecordId, PaymentStatus.SUCCESS, outBizNo,
                              mockTradeNo, null, null);

            log.info("支付宝转账成功(模拟): recordId={}, outBizNo={}, tradeNo={}",
                    paymentRecordId, outBizNo, mockTradeNo);

            // 异步发送成功通知
            notificationService.sendPaymentSuccessNotification(record);

            return mockTradeNo;

        } catch (Exception e) {
            // 异常情况，删除去重标记并记录失败状态
            redisTemplate.delete(dedupKey);
            updatePaymentStatus(paymentRecordId, PaymentStatus.FAILED, outBizNo, null,
                              "SYSTEM_ERROR", e.getMessage());

            log.error("支付宝转账异常: recordId={}, outBizNo={}", paymentRecordId, outBizNo, e);

            // 确保事务回滚（仅当存在事务时）
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }

            // 重新抛出异常以触发事务回滚
            throw e;
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
            paymentBatchService.updateStatus(batch.getId(), BatchStatus.FAILED);

            // 确保事务回滚
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }

            // 重新抛出异常以触发完整回滚
            throw new RuntimeException("批量转账处理异常", e);
        }
    }

    /**
     * 查询转账状态 (暂时模拟实现)
     */
    public PaymentStatus queryTransferStatus(String outBizNo) throws Exception {
        log.info("查询转账状态(模拟): outBizNo={}", outBizNo);

        // TODO: 实际需要调用支付宝查询API
        return PaymentStatus.SUCCESS;
    }

    /**
     * 处理支付宝异步通知
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleNotification(String outTradeNo, String tradeNo, String tradeStatus) {
        log.info("处理支付宝通知: outTradeNo={}, tradeNo={}, status={}",
                outTradeNo, tradeNo, tradeStatus);

        PaymentRecord record = paymentRecordService.getByAlipayOrderNo(outTradeNo);
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

    /**
     * 更新支付记录状态
     */
    private void updatePaymentStatus(Long recordId, PaymentStatus status, String outBizNo,
                                   String tradeNo, String errorCode, String errorMsg) {
        LambdaUpdateWrapper<PaymentRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(PaymentRecord::getId, recordId)
                    .set(PaymentRecord::getStatus, status)
                    .set(PaymentRecord::getAlipayOrderNo, outBizNo);

        if (tradeNo != null) {
            updateWrapper.set(PaymentRecord::getAlipayTradeNo, tradeNo);
        }
        if (errorCode != null) {
            updateWrapper.set(PaymentRecord::getErrorCode, errorCode);
        }
        if (errorMsg != null) {
            updateWrapper.set(PaymentRecord::getErrorMsg, errorMsg);
        }
        if (status == PaymentStatus.SUCCESS) {
            updateWrapper.set(PaymentRecord::getPaymentTime, LocalDateTime.now());
        }

        paymentRecordService.update(updateWrapper);
    }

    /**
     * 验证支付宝签名 (暂时模拟实现)
     */
    public boolean verifyNotification(String notifyData) {
        // TODO: 实现支付宝签名验证
        log.debug("验证支付宝通知签名(模拟): {}", notifyData);
        return true;
    }

    /**
     * 获取支付限额配置
     */
    public BigDecimal getDailyLimit() {
        // TODO: 从配置表读取
        return new BigDecimal("10000.00");
    }

    /**
     * 检查支付限额
     */
    public boolean checkDailyLimit(Long employeeId, BigDecimal amount) {
        // TODO: 实现每日支付限额检查
        BigDecimal dailyLimit = getDailyLimit();
        return amount.compareTo(dailyLimit) <= 0;
    }
}
