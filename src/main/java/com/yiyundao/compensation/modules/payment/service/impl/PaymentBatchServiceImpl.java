package com.yiyundao.compensation.modules.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.enums.BatchStatus;
import com.yiyundao.compensation.infrastructure.dao.PaymentBatchMapper;
import com.yiyundao.compensation.modules.payment.service.PaymentBatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
                    .set(PaymentBatch::getStatus, status);
        update(updateWrapper);
    }
}
