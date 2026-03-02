package com.yiyundao.compensation.modules.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.modules.payment.entity.PaymentRecord;
import com.yiyundao.compensation.enums.PaymentStatus;
import com.yiyundao.compensation.infrastructure.dao.PaymentRecordMapper;
import com.yiyundao.compensation.modules.payment.service.PaymentRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class PaymentRecordServiceImpl extends ServiceImpl<PaymentRecordMapper, PaymentRecord>
        implements PaymentRecordService {

    @Override
    public List<PaymentRecord> getByBatchNo(String batchNo, PaymentStatus status) {
        LambdaQueryWrapper<PaymentRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentRecord::getBatchNo, batchNo);
        if (status != null) queryWrapper.eq(PaymentRecord::getStatus, status);
        return list(queryWrapper);
    }

    @Override
    public PaymentRecord getByAlipayOrderNo(String alipayOrderNo) {
        LambdaQueryWrapper<PaymentRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentRecord::getAlipayOrderNo, alipayOrderNo);
        return getOne(queryWrapper);
    }

    @Override
    public PaymentRecord getByProviderOrderNo(String providerCode, String providerOrderNo) {
        if (!StringUtils.hasText(providerCode) || !StringUtils.hasText(providerOrderNo)) {
            return null;
        }
        LambdaQueryWrapper<PaymentRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentRecord::getProviderCode, providerCode.toLowerCase())
                .eq(PaymentRecord::getProviderOrderNo, providerOrderNo)
                .last("limit 1");
        return getOne(queryWrapper);
    }
}
