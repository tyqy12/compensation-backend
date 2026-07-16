package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.approval.entity.ApprovalWorkflow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ApprovalWorkflowMapper extends BaseMapper<ApprovalWorkflow> {

    @Select("""
            SELECT aw.*
            FROM approval_workflow aw
            INNER JOIN payroll_line pl ON pl.dispute_workflow_id = aw.id
            WHERE aw.deleted = 0
              AND pl.deleted = 0
              AND pl.confirmation_status = 'objected'
              AND aw.status IN ('approved', 'rejected', 'cancelled')
              AND aw.complete_time IS NOT NULL
              AND aw.complete_time <= #{cutoff}
              AND (aw.workflow_type = 'PAYROLL_DISPUTE'
                   OR LOWER(aw.business_type) = 'payroll_dispute')
            ORDER BY aw.complete_time ASC
            LIMIT #{limit}
            """)
    List<ApprovalWorkflow> selectPendingPayrollDisputeWorkflows(@Param("cutoff") LocalDateTime cutoff,
                                                                 @Param("limit") int limit);
}
