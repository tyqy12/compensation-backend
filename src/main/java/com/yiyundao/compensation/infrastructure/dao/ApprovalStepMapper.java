package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.approval.entity.ApprovalStep;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApprovalStepMapper extends BaseMapper<ApprovalStep> {
}

