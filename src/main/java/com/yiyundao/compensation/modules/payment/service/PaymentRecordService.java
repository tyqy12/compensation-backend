package com.yiyundao.compensation.modules.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.enums.PaymentStatus;

import java.util.List;

public interface PaymentRecordService extends IService<PaymentRecord> {
    List<PaymentRecord> getByBatchNo(String batchNo, PaymentStatus status);
    PaymentRecord getByAlipayOrderNo(String alipayOrderNo);
    PaymentRecord getByProviderOrderNo(String providerCode, String providerOrderNo);
}
