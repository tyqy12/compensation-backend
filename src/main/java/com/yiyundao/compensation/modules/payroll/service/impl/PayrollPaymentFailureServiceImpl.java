package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.infrastructure.dao.PayrollPaymentFailureMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollPaymentFailure;
import com.yiyundao.compensation.modules.payroll.service.PayrollBatchService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentFailureService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollPaymentFailureServiceImpl
        extends ServiceImpl<PayrollPaymentFailureMapper, PayrollPaymentFailure>
        implements PayrollPaymentFailureService {

    public static final String STATUS_UNRESOLVED = "unresolved";
    public static final String STATUS_RETRYING = "retrying";
    public static final String STATUS_RESOLVED = "resolved";

    private final ObjectProvider<PayrollBatchService> payrollBatchServiceProvider;
    private final ObjectProvider<PayrollPaymentService> payrollPaymentServiceProvider;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PayrollPaymentFailure recordFailure(Long workflowId, Long payrollBatchId, String businessKey, String errorMessage) {
        if (workflowId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "审批工作流ID不能为空");
        }

        PayrollPaymentFailure failure = getOne(new LambdaQueryWrapper<PayrollPaymentFailure>()
                .eq(PayrollPaymentFailure::getWorkflowId, workflowId)
                .last("limit 1"));
        if (failure == null) {
            failure = new PayrollPaymentFailure();
            failure.setWorkflowId(workflowId);
            failure.setPayrollBatchId(payrollBatchId);
            failure.setBusinessKey(businessKey);
            failure.setRetryCount(0);
        }

        failure.setPayrollBatchId(payrollBatchId);
        failure.setBusinessKey(businessKey);
        failure.setErrorMessage(trimError(errorMessage));
        failure.setStatus(STATUS_UNRESOLVED);
        failure.setLastFailedTime(LocalDateTime.now());
        failure.setResolvedTime(null);
        saveOrUpdate(failure);
        return failure;
    }

    @Override
    @Transactional
    public void markRetrying(Long workflowId, String paymentBatchNo) {
        if (workflowId == null) {
            return;
        }
        PayrollPaymentFailure failure = getOne(new LambdaQueryWrapper<PayrollPaymentFailure>()
                .eq(PayrollPaymentFailure::getWorkflowId, workflowId)
                .ne(PayrollPaymentFailure::getStatus, STATUS_RESOLVED)
                .last("limit 1"));
        if (failure == null) {
            return;
        }
        markRetrying(failure, paymentBatchNo);
    }

    @Override
    @Transactional
    public void markRetryingByPayrollBatchId(Long payrollBatchId, String paymentBatchNo) {
        if (payrollBatchId == null) {
            return;
        }
        PayrollPaymentFailure failure = getOne(new LambdaQueryWrapper<PayrollPaymentFailure>()
                .eq(PayrollPaymentFailure::getPayrollBatchId, payrollBatchId)
                .ne(PayrollPaymentFailure::getStatus, STATUS_RESOLVED)
                .orderByDesc(PayrollPaymentFailure::getLastFailedTime)
                .last("limit 1"));
        if (failure == null) {
            return;
        }
        markRetrying(failure, paymentBatchNo);
    }

    private void markRetrying(PayrollPaymentFailure failure, String paymentBatchNo) {
        failure.setStatus(STATUS_RETRYING);
        failure.setResolvedTime(null);
        failure.setErrorMessage(null);
        if (StringUtils.hasText(paymentBatchNo)) {
            failure.setPaymentBatchNo(paymentBatchNo);
        }
        updateById(failure);
    }

    @Override
    @Transactional
    public void markResolvedByPaymentBatchNo(String paymentBatchNo) {
        if (!StringUtils.hasText(paymentBatchNo)) {
            return;
        }
        PayrollPaymentFailure failure = getOne(new LambdaQueryWrapper<PayrollPaymentFailure>()
                .eq(PayrollPaymentFailure::getPaymentBatchNo, paymentBatchNo)
                .ne(PayrollPaymentFailure::getStatus, STATUS_RESOLVED)
                .orderByDesc(PayrollPaymentFailure::getLastFailedTime)
                .last("limit 1"));
        if (failure == null) {
            return;
        }
        failure.setStatus(STATUS_RESOLVED);
        failure.setResolvedTime(LocalDateTime.now());
        failure.setErrorMessage(null);
        updateById(failure);
    }

    @Override
    @Transactional
    public void markUnresolvedByPaymentBatchNo(String paymentBatchNo, String errorMessage) {
        if (!StringUtils.hasText(paymentBatchNo)) {
            return;
        }
        PayrollPaymentFailure failure = getOne(new LambdaQueryWrapper<PayrollPaymentFailure>()
                .eq(PayrollPaymentFailure::getPaymentBatchNo, paymentBatchNo)
                .ne(PayrollPaymentFailure::getStatus, STATUS_RESOLVED)
                .orderByDesc(PayrollPaymentFailure::getLastFailedTime)
                .last("limit 1"));
        if (failure == null) {
            return;
        }
        failure.setStatus(STATUS_UNRESOLVED);
        failure.setResolvedTime(null);
        failure.setLastFailedTime(LocalDateTime.now());
        failure.setErrorMessage(trimError(errorMessage));
        updateById(failure);
    }

    @Override
    @Transactional
    public PayrollPaymentFailure retry(Long failureId, boolean triggerTransfer) {
        PayrollPaymentFailure failure = getById(failureId);
        if (failure == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "支付补偿记录不存在");
        }
        if (failure.isResolved()) {
            return failure;
        }
        if (failure.getPayrollBatchId() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "补偿记录缺少薪资批次ID，不能自动重试");
        }
        if (STATUS_RETRYING.equalsIgnoreCase(failure.getStatus())) {
            return failure;
        }
        PayrollBatchService payrollBatchService = payrollBatchServiceProvider.getObject();
        PayrollBatch payrollBatch = payrollBatchService.getById(failure.getPayrollBatchId());
        if (payrollBatch == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "薪资批次不存在，不能自动重试");
        }

        int nextRetryCount = (failure.getRetryCount() == null ? 0 : failure.getRetryCount()) + 1;
        LocalDateTime retryTime = LocalDateTime.now();
        if (!claimRetry(failure, nextRetryCount, retryTime)) {
            return getById(failureId);
        }
        failure.setStatus(STATUS_RETRYING);
        failure.setLastRetryTime(retryTime);
        failure.setRetryCount(nextRetryCount);

        boolean created;
        if (StringUtils.hasText(payrollBatch.getPaymentBatchNo())) {
            payrollPaymentServiceProvider.getObject()
                    .retryFailedPayment(failure.getPayrollBatchId(), triggerTransfer);
            created = true;
            failure.setPaymentBatchNo(payrollBatch.getPaymentBatchNo());
        } else {
            created = payrollBatchService.retryCreatePaymentBatch(failure.getPayrollBatchId(), triggerTransfer);
            if (created) {
                PayrollBatch latest = payrollBatchService.getById(failure.getPayrollBatchId());
                if (latest != null && StringUtils.hasText(latest.getPaymentBatchNo())) {
                    failure.setPaymentBatchNo(latest.getPaymentBatchNo());
                }
            }
        }
        if (!created) {
            failure.setStatus(STATUS_UNRESOLVED);
            failure.setErrorMessage("重试未创建支付批次，请检查薪资批次状态和收款数据");
            failure.setLastFailedTime(LocalDateTime.now());
            updateById(failure);
            return failure;
        }

        failure.setStatus(STATUS_RETRYING);
        failure.setResolvedTime(null);
        failure.setErrorMessage(null);
        updateById(failure);
        return failure;
    }

    private boolean claimRetry(PayrollPaymentFailure failure, int nextRetryCount, LocalDateTime retryTime) {
        return update(new UpdateWrapper<PayrollPaymentFailure>()
                .eq("id", failure.getId())
                .ne("status", STATUS_RESOLVED)
                .ne("status", STATUS_RETRYING)
                .set("status", STATUS_RETRYING)
                .set("last_retry_time", retryTime)
                .set("retry_count", nextRetryCount));
    }

    @Override
    public List<PayrollPaymentFailure> listUnresolved(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return list(new LambdaQueryWrapper<PayrollPaymentFailure>()
                .ne(PayrollPaymentFailure::getStatus, STATUS_RESOLVED)
                .orderByDesc(PayrollPaymentFailure::getLastFailedTime)
                .last("limit " + safeLimit));
    }

    private String trimError(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return null;
        }
        return errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
    }
}
