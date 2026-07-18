package com.yiyundao.compensation.modules.payroll.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollLineMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 结算终态的完整性边界。
 *
 * <p>支付成功后只允许写入一次结果摘要和不可变标志；后续差异必须通过调整批次处理。</p>
 */
@Service
@RequiredArgsConstructor
public class PayrollSettlementIntegrityService {

    private final PayrollBatchMapper payrollBatchMapper;
    private final PayrollLineMapper payrollLineMapper;

    @Transactional
    public boolean finalizeByPaymentBatchNo(String paymentBatchNo,
                                             PayrollBatchStatus targetStatus,
                                             String paymentStatus) {
        if (!StringUtils.hasText(paymentBatchNo) || targetStatus == null) {
            return false;
        }
        PayrollBatch batch = payrollBatchMapper.selectOne(new QueryWrapper<PayrollBatch>()
                .eq("payment_batch_no", paymentBatchNo));
        if (batch == null) {
            return false;
        }
        if (Boolean.TRUE.equals(batch.getImmutableFlag())) {
            return true;
        }
        if (batch.getStatus() != PayrollBatchStatus.PAY_PROCESSING
                && batch.getStatus() != PayrollBatchStatus.PAY_FAILED) {
            return false;
        }
        return finalizeBatch(batch, targetStatus, paymentStatus);
    }

    @Transactional
    public boolean finalizeBatch(Long batchId, PayrollBatchStatus targetStatus) {
        if (batchId == null || targetStatus == null) {
            return false;
        }
        PayrollBatch batch = payrollBatchMapper.selectById(batchId);
        if (batch == null) {
            return false;
        }
        if (Boolean.TRUE.equals(batch.getImmutableFlag())) {
            return true;
        }
        return finalizeBatch(batch, targetStatus, null);
    }

    private boolean finalizeBatch(PayrollBatch batch,
                                   PayrollBatchStatus targetStatus,
                                   String paymentStatus) {
        if (batch.getStatus() == null || batch.getId() == null) {
            return false;
        }
        boolean immutableTarget = targetStatus == PayrollBatchStatus.PAID
                || targetStatus == PayrollBatchStatus.ARCHIVED;
        if (!immutableTarget && targetStatus != PayrollBatchStatus.PAY_FAILED) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<PayrollBatch> wrapper = new UpdateWrapper<PayrollBatch>()
                .eq("id", batch.getId())
                .eq("status", batch.getStatus().getCode())
                .set("status", targetStatus.getCode());
        if (immutableTarget) {
            wrapper.set("immutable_flag", true)
                    .set("result_hash", calculateResultHash(batch.getId()))
                    .set("input_frozen_at", batch.getInputFrozenAt() == null ? now : batch.getInputFrozenAt())
                    .set("locked_at", batch.getLockedAt() == null ? now : batch.getLockedAt());
        }
        if (StringUtils.hasText(paymentStatus)) {
            wrapper.set("payment_status", paymentStatus);
        }
        if (targetStatus == PayrollBatchStatus.ARCHIVED) {
            wrapper.set("closed_at", batch.getClosedAt() == null ? now : batch.getClosedAt());
        }
        if (batch.getVersion() != null) {
            wrapper.eq("version", batch.getVersion())
                    .setSql("version = version + 1");
        }
        return payrollBatchMapper.update(null, wrapper) > 0;
    }

    private String calculateResultHash(Long batchId) {
        List<PayrollLine> lines = payrollLineMapper.selectList(new QueryWrapper<PayrollLine>()
                .eq("batch_id", batchId)
                .orderByAsc("id"));
        StringBuilder canonical = new StringBuilder();
        for (PayrollLine line : lines) {
            append(canonical, line.getId());
            append(canonical, line.getEmployeeId());
            append(canonical, line.getBatchRevision());
            append(canonical, line.getGrossAmount());
            append(canonical, line.getTaxAmount());
            append(canonical, line.getSocialAmount());
            append(canonical, line.getNetAmount());
            append(canonical, line.getItemsSnapshotJson());
            append(canonical, line.getTaxBreakdownJson());
            canonical.append('\n');
        }
        return sha256(canonical.toString());
    }

    private void append(StringBuilder target, Object value) {
        if (value instanceof BigDecimal decimal) {
            target.append(decimal.toPlainString());
        } else {
            target.append(value == null ? "" : value);
        }
        target.append('|');
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法生成薪酬结算结果摘要", e);
        }
    }
}
