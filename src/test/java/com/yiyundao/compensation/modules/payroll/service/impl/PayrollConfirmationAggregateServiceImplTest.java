package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.enums.PayrollBatchStatus;
import com.yiyundao.compensation.enums.PayrollConfirmationRecordStatus;
import com.yiyundao.compensation.enums.PayrollConfirmationSheetStatus;
import com.yiyundao.compensation.enums.PayrollConfirmationStatus;
import com.yiyundao.compensation.infrastructure.dao.PayrollBatchMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollConfirmationMapper;
import com.yiyundao.compensation.infrastructure.dao.PayrollConfirmationRecordMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollConfirmation;
import com.yiyundao.compensation.modules.payroll.entity.PayrollConfirmationRecord;
import com.yiyundao.compensation.modules.payroll.entity.PayrollLine;
import com.yiyundao.compensation.modules.payroll.service.PayrollLineService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollConfirmationAggregateServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PayrollConfirmation.class);
        initTableInfo(PayrollConfirmationRecord.class);
        initTableInfo(PayrollLine.class);
    }

    private static void initTableInfo(Class<?> entityClass) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }

    @Mock
    private PayrollConfirmationMapper confirmationMapper;
    @Mock
    private PayrollBatchMapper payrollBatchMapper;
    @Mock
    private PayrollConfirmationRecordMapper confirmationRecordMapper;
    @Mock
    private PayrollLineService payrollLineService;

    @Test
    void createOrRefreshForBatchShouldNotTreatConfirmedBatchAsCompletedWhenLinesStillPending() {
        PayrollConfirmationAggregateServiceImpl service = new PayrollConfirmationAggregateServiceImpl(
                payrollBatchMapper,
                confirmationRecordMapper,
                payrollLineService
        );
        ReflectionTestUtils.setField(service, ServiceImpl.class, "baseMapper", confirmationMapper, BaseMapper.class);
        PayrollBatch batch = new PayrollBatch();
        batch.setId(10L);
        batch.setBatchRevision(1);
        batch.setStatus(PayrollBatchStatus.CONFIRMED);
        batch.setConfirmationRequired(Boolean.TRUE);

        PayrollLine confirmedLine = new PayrollLine();
        confirmedLine.setId(1001L);
        confirmedLine.setBatchId(10L);
        confirmedLine.setEmployeeId(2001L);
        confirmedLine.setConfirmationStatus(PayrollConfirmationStatus.CONFIRMED.getCode());

        PayrollLine pendingLine = new PayrollLine();
        pendingLine.setId(1002L);
        pendingLine.setBatchId(10L);
        pendingLine.setEmployeeId(2002L);
        pendingLine.setConfirmationStatus(PayrollConfirmationStatus.PENDING.getCode());

        when(confirmationMapper.selectOne(any(), org.mockito.ArgumentMatchers.eq(true))).thenReturn(null);
        when(confirmationMapper.insert(any(PayrollConfirmation.class))).thenAnswer(invocation -> {
            PayrollConfirmation confirmation = invocation.getArgument(0);
            confirmation.setId(3001L);
            return 1;
        });
        when(payrollLineService.list(org.mockito.ArgumentMatchers.<Wrapper<PayrollLine>>any()))
                .thenReturn(List.of(confirmedLine, pendingLine));
        when(confirmationRecordMapper.selectList(any())).thenReturn(List.of());

        PayrollConfirmation result = service.createOrRefreshForBatch(batch);

        assertThat(result.getConfirmationStatus()).isEqualTo(PayrollConfirmationSheetStatus.CONFIRMING);
        assertThat(result.getTotalEmployees()).isEqualTo(2);
        assertThat(result.getConfirmedCount()).isEqualTo(1);
        ArgumentCaptor<PayrollConfirmation> captor = ArgumentCaptor.forClass(PayrollConfirmation.class);
        verify(confirmationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getConfirmationStatus()).isEqualTo(PayrollConfirmationSheetStatus.CONFIRMING);
    }

    @Test
    void createOrRefreshForBatchShouldTreatRejectedDisputeAsPendingReconfirmation() {
        PayrollConfirmationAggregateServiceImpl service = new PayrollConfirmationAggregateServiceImpl(
                payrollBatchMapper,
                confirmationRecordMapper,
                payrollLineService
        );
        ReflectionTestUtils.setField(service, ServiceImpl.class, "baseMapper", confirmationMapper, BaseMapper.class);
        PayrollBatch batch = new PayrollBatch();
        batch.setId(11L);
        batch.setBatchRevision(1);
        batch.setStatus(PayrollBatchStatus.CONFIRMING);
        batch.setConfirmationRequired(Boolean.TRUE);

        PayrollLine line = new PayrollLine();
        line.setId(1101L);
        line.setBatchId(11L);
        line.setEmployeeId(2101L);
        line.setConfirmationStatus(PayrollConfirmationStatus.OBJECTED_REJECTED.getCode());
        line.setObjectionReason("金额不符");

        when(confirmationMapper.selectOne(any(), org.mockito.ArgumentMatchers.eq(true))).thenReturn(null);
        when(confirmationMapper.insert(any(PayrollConfirmation.class))).thenAnswer(invocation -> {
            PayrollConfirmation confirmation = invocation.getArgument(0);
            confirmation.setId(3002L);
            return 1;
        });
        when(payrollLineService.list(org.mockito.ArgumentMatchers.<Wrapper<PayrollLine>>any()))
                .thenReturn(List.of(line));
        when(confirmationRecordMapper.selectList(any())).thenReturn(List.of());

        PayrollConfirmation result = service.createOrRefreshForBatch(batch);

        assertThat(result.getConfirmationStatus()).isEqualTo(PayrollConfirmationSheetStatus.CONFIRMING);
        assertThat(result.getConfirmedCount()).isZero();
        assertThat(result.getRejectedCount()).isZero();
        ArgumentCaptor<PayrollConfirmationRecord> recordCaptor =
                ArgumentCaptor.forClass(PayrollConfirmationRecord.class);
        verify(confirmationRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getRecordStatus()).isEqualTo(PayrollConfirmationRecordStatus.PENDING);
        assertThat(recordCaptor.getValue().getRejectReason()).isNull();
    }
}
