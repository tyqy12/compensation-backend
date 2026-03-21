package com.yiyundao.compensation.modules.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.enums.PaymentBatchProcessStatus;
import com.yiyundao.compensation.enums.PaymentType;
import com.yiyundao.compensation.infrastructure.dao.PaymentBatchMapper;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Slf4j
@Service
public class PaymentBatchServiceImpl extends ServiceImpl<PaymentBatchMapper, PaymentBatch>
        implements PaymentBatchService {

    @Override
    public PaymentBatch getByBatchNo(String batchNo) {
        LambdaQueryWrapper<PaymentBatch> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentBatch::getBatchNo, batchNo);
        return getOne(queryWrapper);
    }

    @Override
    public void updateStatus(Long batchId, BatchStatus status) {
        LambdaUpdateWrapper<PaymentBatch> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(PaymentBatch::getId, batchId)
                    .set(PaymentBatch::getStatus, status)
                    .set(PaymentBatch::getPaymentStatus, mapPaymentStatus(status));
        update(updateWrapper);
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

    @Override
    public Page<PaymentBatch> pagePaymentBatches(int pageNum, int pageSize, String keyword, String status,
                                                 String paymentType, String startDate, String endDate,
                                                 String sortBy, String order) {
        log.info("分页查询支付批次: page={}, size={}, keyword={}, status={}, paymentType={}",
                pageNum, pageSize, keyword, status, paymentType);

        Page<PaymentBatch> page = new Page<>(pageNum, pageSize);
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
                BatchStatus batchStatus = BatchStatus.fromCode(status);
                queryWrapper.eq(PaymentBatch::getStatus, batchStatus);
            } catch (IllegalArgumentException e) {
                log.warn("无效的批次状态: {}", status);
            }
        }

        // 支付类型筛选
        if (StringUtils.hasText(paymentType)) {
            try {
                PaymentType type = PaymentType.fromCode(paymentType);
                queryWrapper.eq(PaymentBatch::getPaymentType, type);
            } catch (IllegalArgumentException e) {
                log.warn("无效的支付类型: {}", paymentType);
            }
        }

        // 日期范围筛选（基于提交时间）
        if (StringUtils.hasText(startDate)) {
            try {
                LocalDate start = LocalDate.parse(startDate);
                queryWrapper.ge(PaymentBatch::getSubmitTime, start.atStartOfDay());
            } catch (Exception e) {
                log.warn("无效的开始日期: {}", startDate);
            }
        }

        if (StringUtils.hasText(endDate)) {
            try {
                LocalDate end = LocalDate.parse(endDate);
                queryWrapper.le(PaymentBatch::getSubmitTime, end.atTime(23, 59, 59));
            } catch (Exception e) {
                log.warn("无效的结束日期: {}", endDate);
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
}
