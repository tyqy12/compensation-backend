package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollBatch;
import com.yiyundao.compensation.modules.payroll.entity.PayrollTaxLedger;
import com.yiyundao.compensation.modules.payroll.service.PayrollCumulativeTaxService;
import com.yiyundao.compensation.modules.payroll.service.PayrollPolicyPackageService;
import com.yiyundao.compensation.modules.payroll.service.PayrollTaxBracketService;
import com.yiyundao.compensation.modules.payroll.service.PayrollTaxDeductionDeclarationService;
import com.yiyundao.compensation.modules.payroll.service.PayrollTaxLedgerService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollCumulativeTaxServiceImplTest {

    @Mock
    private PayrollTaxLedgerService taxLedgerService;
    @Mock
    private PayrollTaxDeductionDeclarationService declarationService;
    @Mock
    private PayrollPolicyPackageService policyPackageService;
    @Mock
    private PayrollTaxBracketService taxBracketService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, PayrollTaxLedger.class.getName());
        assistant.setCurrentNamespace(PayrollTaxLedger.class.getName());
        TableInfoHelper.initTableInfo(assistant, PayrollTaxLedger.class);
    }

    @Test
    void shouldStorePreviousAndCurrentWithheldTaxAsCumulativeLedger() {
        PayrollTaxLedger previous = new PayrollTaxLedger();
        previous.setCumulativeIncome(new BigDecimal("10000.00"));
        previous.setCumulativeBasicDeduction(new BigDecimal("5000.00"));
        previous.setCumulativeWithheldTax(new BigDecimal("10.00"));
        previous.setCumulativeTaxReduction(BigDecimal.ZERO);
        when(taxLedgerService.findLatestBefore(7L, null, 2026, 2)).thenReturn(previous);
        when(declarationService.list(org.mockito.ArgumentMatchers.<Wrapper<com.yiyundao.compensation.modules.payroll.entity.PayrollTaxDeductionDeclaration>>any()))
                .thenReturn(List.of());
        when(policyPackageService.getOne(any())).thenReturn(null);
        when(taxLedgerService.getOne(any())).thenReturn(null);
        when(taxLedgerService.saveOrUpdate(any(PayrollTaxLedger.class))).thenReturn(true);

        PayrollBatch batch = new PayrollBatch();
        batch.setId(100L);
        batch.setBatchRevision(1);
        batch.setPeriodLabel("2026-02");

        PayrollCumulativeTaxServiceImpl service = new PayrollCumulativeTaxServiceImpl(
                taxLedgerService, declarationService, policyPackageService, taxBracketService);
        PayrollCumulativeTaxService.TaxComputation computation = service.calculate(
                7L, batch, new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 2, RoundingMode.HALF_UP);

        assertThat(computation.result().currentWithholdingTax()).isEqualByComparingTo("290.00");
        service.recordLedger(7L, batch, 1001L, computation);

        ArgumentCaptor<PayrollTaxLedger> captor = ArgumentCaptor.forClass(PayrollTaxLedger.class);
        verify(taxLedgerService).saveOrUpdate(captor.capture());
        assertThat(captor.getValue().getCurrentWithholdingTax()).isEqualByComparingTo("290.00");
        assertThat(captor.getValue().getCumulativeWithheldTax()).isEqualByComparingTo("300.00");
        assertThat(captor.getValue().getPayrollBatchRevision()).isEqualTo(1);
    }
}
