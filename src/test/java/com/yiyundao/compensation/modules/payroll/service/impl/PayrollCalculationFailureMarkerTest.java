package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yiyundao.compensation.enums.PayrollCalculationStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PayrollCalculationFailureMarkerTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, PayrollBatch.class.getName());
        assistant.setCurrentNamespace(PayrollBatch.class.getName());
        TableInfoHelper.initTableInfo(assistant, PayrollBatch.class);
    }

    @Mock
    private PayrollBatchMapper payrollBatchMapper;

    @Test
    void markFailedShouldGuardByVersionWhenPresent() {
        PayrollCalculationFailureMarker marker = new PayrollCalculationFailureMarker(payrollBatchMapper);

        marker.markFailed(42L, LocalDateTime.parse("2026-06-05T12:00:00"), 7);

        ArgumentCaptor<LambdaUpdateWrapper<PayrollBatch>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(payrollBatchMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<PayrollBatch> wrapper = captor.getValue();
        wrapper.getSqlSegment();
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(42L, 7, PayrollCalculationStatus.FAILED);
    }
}
