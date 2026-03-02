package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.payment.entity.SettlementProviderConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 结算渠道配置 Mapper
 */
@Mapper
public interface SettlementProviderConfigMapper extends BaseMapper<SettlementProviderConfig> {
}
