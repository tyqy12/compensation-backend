package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.infrastructure.dao.PayrollEnrollmentMapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollEnrollment;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollEnrollmentServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, PayrollEnrollment.class.getName());
        assistant.setCurrentNamespace(PayrollEnrollment.class.getName());
        TableInfoHelper.initTableInfo(assistant, PayrollEnrollment.class);
    }

    @Mock
    private PayrollEnrollmentMapper mapper;

    @Test
    void shouldRejectOverlappingSameInsuranceRelationship() {
        PayrollEnrollment existing = new PayrollEnrollment();
        existing.setId(1L);
        existing.setEmployeeId(10L);
        existing.setContributionType("pension");
        existing.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        existing.setEffectiveTo(LocalDate.of(2026, 12, 31));
        existing.setStatus("active");

        when(mapper.selectList(any())).thenReturn(List.of(existing));
        PayrollEnrollmentServiceImpl service = new PayrollEnrollmentServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        PayrollEnrollment next = new PayrollEnrollment();
        next.setEmployeeId(10L);
        next.setContributionType("pension");
        next.setEffectiveFrom(LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> service.saveValidated(next))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重叠");
    }
}
