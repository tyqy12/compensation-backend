package com.yiyundao.compensation.modules.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import com.yiyundao.compensation.enums.BatchStatus;

public interface PaymentBatchService extends IService<PaymentBatch> {
    PaymentBatch getByBatchNo(String batchNo);
    void updateStatus(Long batchId, BatchStatus status);
}
