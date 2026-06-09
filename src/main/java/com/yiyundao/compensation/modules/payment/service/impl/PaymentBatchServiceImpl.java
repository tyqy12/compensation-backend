package com.yiyundao.compensation.modules.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PaymentType;
import com.yiyundao.compensation.infrastructure.dao.PaymentBatchMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.modules.payroll.service.PayrollDistributionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentBatchServiceImpl extends ServiceImpl<PaymentBatchMapper, PaymentBatch>
        implements PaymentBatchService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    private final PayrollBatchMapper payrollBatchMapper;
    private final ObjectProvider<PayrollDistributionService> payrollDistributionServiceProvider;

    @Override
    public PaymentBatch getByBatchNo(String batchNo) {
        LambdaQueryWrapper<PaymentBatch> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentBatch::getBatchNo, batchNo);
        return getOne(queryWrapper);
    }

    @Override
    public void updateStatus(Long batchId, BatchStatus status) {
        UpdateWrapper<PaymentBatch> updateWrapper = new UpdateWrapper<PaymentBatch>()
                .eq("id", batchId)
                .set("status", status == null ? null : status.getCode())
                .set("payment_status", mapPaymentStatus(status) == null ? null : mapPaymentStatus(status).getCode());
        update(updateWrapper);
        syncTerminalRelations(batchId, status);
    }

    @Override
    public void updateTerminalState(PaymentBatch batch) {
        if (batch == null || batch.getId() == null) {
            return;
        }
        updateById(batch);
        syncPayrollBatchStatus(batch);
        syncDistributionStatus(batch);
    }

    private PaymentBatchProcessStatus mapPaymentStatus(BatchStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case DRAFT -> PaymentBatchProcessStatus.CREATED;
            case SUBMITTED, APPROVED -> PaymentBatchProcessStatus.SUBMITTED;
            case PROCESSING -> PaymentBatchProcessStatus.PROCESSING;
            case COMPLETED -> PaymentBatchProcessStatus.SUCCESS;
            case FAILED -> PaymentBatchProcessStatus.FAILED;
        };
    }

    private void syncTerminalRelations(Long batchId, BatchStatus status) {
        if (batchId == null || status == null || (status != BatchStatus.COMPLETED && status != BatchStatus.FAILED)) {
            return;
        }
        PaymentBatch batch = getById(batchId);
        if (batch == null || !StringUtils.hasText(batch.getBatchNo())) {
            return;
        }
        payrollBatchMapper.update(null, new UpdateWrapper<com.yiyundao.compensation.modules.payroll.entity.PayrollBatch>()
                .eq("payment_batch_no", batch.getBatchNo())
                .in("status", PayrollBatchStatus.PAY_PROCESSING.getCode(), PayrollBatchStatus.PAY_FAILED.getCode())
                .set("status", resolvePayrollBatchStatus(batch).getCode()));
        syncDistributionStatus(batch);
    }

    private void syncPayrollBatchStatus(PaymentBatch batch) {
        if (batch == null || !StringUtils.hasText(batch.getBatchNo())) {
            return;
        }
        BatchStatus status = batch.getStatus();
        if (status != BatchStatus.COMPLETED && status != BatchStatus.FAILED) {
            return;
        }
        boolean partialSuccess = batch.getPaymentStatus() == PaymentBatchProcessStatus.PARTIAL_SUCCESS
                || (batch.getSuccessCount() != null && batch.getSuccessCount() > 0
                && batch.getFailedCount() != null && batch.getFailedCount() > 0);
        payrollBatchMapper.update(null, new UpdateWrapper<com.yiyundao.compensation.modules.payroll.entity.PayrollBatch>()
                .eq("payment_batch_no", batch.getBatchNo())
                .in("status", PayrollBatchStatus.PAY_PROCESSING.getCode(), PayrollBatchStatus.PAY_FAILED.getCode())
                .set("status", resolvePayrollBatchStatus(status, partialSuccess).getCode()));
    }

    private PayrollBatchStatus resolvePayrollBatchStatus(PaymentBatch batch) {
        if (batch == null) {
            return PayrollBatchStatus.PAY_FAILED;
        }
        boolean partialSuccess = batch.getPaymentStatus() == PaymentBatchProcessStatus.PARTIAL_SUCCESS
                || (batch.getSuccessCount() != null && batch.getSuccessCount() > 0
                && batch.getFailedCount() != null && batch.getFailedCount() > 0);
        return resolvePayrollBatchStatus(batch.getStatus(), partialSuccess);
    }

    private PayrollBatchStatus resolvePayrollBatchStatus(BatchStatus status, boolean partialSuccess) {
        return status == BatchStatus.COMPLETED && !partialSuccess
                ? PayrollBatchStatus.PAID
                : PayrollBatchStatus.PAY_FAILED;
    }

    private void syncDistributionStatus(PaymentBatch batch) {
        if (batch == null || batch.getDistributionId() == null) {
            return;
        }
        PayrollDistributionService payrollDistributionService = payrollDistributionServiceProvider.getIfAvailable();
        if (payrollDistributionService == null) {
            return;
        }
        payrollDistributionService.syncFromPaymentBatch(batch);
    }

    @Override
    public Page<PaymentBatch> pagePaymentBatches(int pageNum, int pageSize, String keyword, String status,
                                                 String paymentType, String startDate, String endDate,
                                                 String sortBy, String order) {
        log.info("分页查询支付批次: page={}, size={}, keyword={}, status={}, paymentType={}",
                pageNum, pageSize, keyword, status, paymentType);

        Page<PaymentBatch> page = new Page<>(safePage(pageNum), safeSize(pageSize));
        LambdaQueryWrapper<PaymentBatch> queryWrapper = new LambdaQueryWrapper<>();

        // 关键字搜索（批次号或批次名称）
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper ->
                wrapper.like(PaymentBatch::getBatchNo, keyword)
                       .or()
                       .like(PaymentBatch::getBatchName, keyword)
            );
        }

        // 状态筛选
        if (StringUtils.hasText(status)) {
            try {
                BatchStatus batchStatus = BatchStatus.fromCode(status.trim());
                queryWrapper.eq(PaymentBatch::getStatus, batchStatus);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的批次状态: " + status);
            }
        }

        // 支付类型筛选
        if (StringUtils.hasText(paymentType)) {
            try {
                PaymentType type = PaymentType.fromCode(paymentType.trim());
                queryWrapper.eq(PaymentBatch::getPaymentType, type);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的支付类型: " + paymentType);
            }
        }

        // 日期范围筛选（基于提交时间）
        if (StringUtils.hasText(startDate)) {
            try {
                LocalDate start = LocalDate.parse(startDate.trim());
                queryWrapper.ge(PaymentBatch::getSubmitTime, start.atStartOfDay());
            } catch (DateTimeParseException e) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的开始日期: " + startDate);
            }
        }

        if (StringUtils.hasText(endDate)) {
            try {
                LocalDate end = LocalDate.parse(endDate.trim());
                queryWrapper.le(PaymentBatch::getSubmitTime, end.atTime(23, 59, 59));
            } catch (DateTimeParseException e) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的结束日期: " + endDate);
            }
        }

        // 排序
        if (StringUtils.hasText(sortBy)) {
            boolean isAsc = "asc".equalsIgnoreCase(order);
            switch (sortBy) {
                case "submitTime":
                    queryWrapper.orderBy(true, isAsc, PaymentBatch::getSubmitTime);
                    break;
                case "createTime":
                    queryWrapper.orderBy(true, isAsc, PaymentBatch::getCreateTime);
                    break;
                case "totalAmount":
                    queryWrapper.orderBy(true, isAsc, PaymentBatch::getTotalAmount);
                    break;
                case "batchName":
                    queryWrapper.orderBy(true, isAsc, PaymentBatch::getBatchName);
                    break;
                default:
                    queryWrapper.orderByDesc(PaymentBatch::getSubmitTime);
            }
        } else {
            queryWrapper.orderByDesc(PaymentBatch::getSubmitTime);
        }

        return page(page, queryWrapper);
    }

    private int safePage(int pageNum) {
        return pageNum < 1 ? 1 : pageNum;
    }

    private int safeSize(int pageSize) {
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
