package com.yiyundao.compensation.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyundao.compensation.interfaces.vo.payroll.PayrollBatchSummaryVO;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PayrollBatchMapper extends BaseMapper<PayrollBatch> {

    /**
     * 查询批次列表并聚合汇总数据
     */
    List<PayrollBatchSummaryVO> selectBatchSummaryList(
            @Param("type") String type,
            @Param("periodLabel") String periodLabel,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("size") int size
    );

    /**
     * 统计符合条件的批次数量
     */
    long countBatchSummary(@Param("type") String type, @Param("periodLabel") String periodLabel, @Param("status") String status);
}


