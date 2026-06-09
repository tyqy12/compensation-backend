package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.modules.payroll.entity.PayrollApprovalProjection;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PayrollApprovalProjectionServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, PayrollApprovalProjection.class.getName());
        assistant.setCurrentNamespace(PayrollApprovalProjection.class.getName());
        TableInfoHelper.initTableInfo(assistant, PayrollApprovalProjection.class);
    }

    @Test
    void getByDistributionIdShouldPreferLatestProjectionWhenHistoricalRowsExist() {
        PayrollApprovalProjectionServiceImpl service = new PayrollApprovalProjectionServiceImpl();
        BaseMapper<PayrollApprovalProjection> baseMapper = mock(BaseMapper.class);
        ReflectionTestUtils.setField(service, ServiceImpl.class, "baseMapper", baseMapper, BaseMapper.class);

        service.getByDistributionId(3001L);

        ArgumentCaptor<LambdaQueryWrapper<PayrollApprovalProjection>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(baseMapper).selectOne(wrapperCaptor.capture(), any(Boolean.class));
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sqlSegment)
                .contains("distribution_id")
                .contains("ORDER BY")
                .contains("update_time DESC")
                .contains("id DESC");
    }

    @Test
    void markCancelledShouldOnlyMoveInProgressProjectionToFinalState() {
        PayrollApprovalProjectionServiceImpl service = new PayrollApprovalProjectionServiceImpl();
        BaseMapper<PayrollApprovalProjection> baseMapper = mock(BaseMapper.class);
        ReflectionTestUtils.setField(service, ServiceImpl.class, "baseMapper", baseMapper, BaseMapper.class);

        service.markCancelled(9001L, 8001L, "CANCELLED");

        ArgumentCaptor<UpdateWrapper<PayrollApprovalProjection>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(baseMapper).update(eq(null), wrapperCaptor.capture());
        UpdateWrapper<PayrollApprovalProjection> wrapper = wrapperCaptor.getValue();
        wrapper.getSqlSegment();
        assertThat(wrapper.getExpression().getNormal().getSqlSegment())
                .contains("workflow_id =", "business_status =");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(9001L, "IN_PROGRESS", "CANCELLED", 8001L)
                .doesNotContain("APPROVED");
    }
}
