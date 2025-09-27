package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.payment.entity.PaymentBatch;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentBatchMapper extends BaseMapper<PaymentBatch> {
}

