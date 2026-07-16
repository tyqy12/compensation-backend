package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollApprovalProjection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PayrollApprovalProjectionMapper extends BaseMapper<PayrollApprovalProjection> {

    @Select("""
            SELECT pap.*
            FROM payroll_approval_projection pap
            INNER JOIN payroll_distribution pd ON pd.id = pap.distribution_id
            LEFT JOIN approval_workflow aw ON aw.id = pap.workflow_id AND aw.deleted = 0
            WHERE pap.deleted = 0
              AND pd.deleted = 0
              AND pd.distribution_status = 'planned'
              AND (pap.workflow_id < 0
                   OR aw.workflow_type = 'PAYROLL_DISTRIBUTION'
                   OR LOWER(aw.business_type) = 'payroll_distribution')
              AND (
                    (
                      pap.business_status = 'IN_PROGRESS'
                      AND aw.status = 'approved'
                      AND aw.complete_time IS NOT NULL
                      AND aw.complete_time <= #{cutoff}
                    )
                    OR (
                      pap.business_status = 'APPROVED'
                      AND (
                            pap.workflow_id < 0
                            OR (
                              aw.status = 'approved'
                              AND aw.complete_time IS NOT NULL
                              AND aw.complete_time <= #{cutoff}
                            )
                          )
                      AND (pd.scheduled_date IS NULL OR pd.scheduled_date <= CURRENT_DATE)
                    )
                  )
            ORDER BY COALESCE(aw.complete_time, pap.completed_at, pap.update_time) ASC
            LIMIT #{limit}
            """)
    List<PayrollApprovalProjection> selectStalePendingDistributionApprovals(
            @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
