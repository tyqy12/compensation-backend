package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.notification.entity.NotificationRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationRecordMapper extends BaseMapper<NotificationRecord> {
}