package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yiyundao.compensation.enums.PayrollCalculationStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PayrollCalculationFailureMarker {

    private final PayrollBatchMapper payrollBatchMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long batchId, LocalDateTime guardUpdateTime, Integer guardVersion) {
        if (batchId == null) {
            return;
        }
        LambdaUpdateWrapper<PayrollBatch> wrapper = new LambdaUpdateWrapper<PayrollBatch>()
                .eq(PayrollBatch::getId, batchId)
                .set(PayrollBatch::getCalculationStatus, PayrollCalculationStatus.FAILED)
                .set(PayrollBatch::getUpdateTime, LocalDateTime.now());
        if (guardVersion != null) {
            wrapper.eq(PayrollBatch::getVersion, guardVersion);
        } else if (guardUpdateTime != null) {
            wrapper.eq(PayrollBatch::getUpdateTime, guardUpdateTime);
        }
        payrollBatchMapper.update(null, wrapper);
    }
}
