package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApprovalWorkflowMapper extends BaseMapper<ApprovalWorkflow> {
}

