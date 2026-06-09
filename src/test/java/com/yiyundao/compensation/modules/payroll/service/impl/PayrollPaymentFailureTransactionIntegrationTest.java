package com.yiyundao.compensation.modules.payroll.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.modules.payroll.entity.PayrollPaymentFailure;
import com.yiyundao.compensation.modules.payroll.service.PayrollPaymentFailureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PayrollPaymentFailureTransactionIntegrationTest {

    @Autowired
    private PayrollPaymentFailureService payrollPaymentFailureService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void recordFailureShouldSurviveCallerTransactionRollback() {
        Long workflowId = 99_000_000L + System.nanoTime() % 1_000_000L;
        payrollPaymentFailureService.remove(new LambdaQueryWrapper<PayrollPaymentFailure>()
                .eq(PayrollPaymentFailure::getWorkflowId, workflowId));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            payrollPaymentFailureService.recordFailure(
                    workflowId,
                    20L,
                    "payroll_batch:20",
                    "payment creation failed"
            );
            throw new IllegalStateException("force caller rollback");
        })).isInstanceOf(IllegalStateException.class);

        List<PayrollPaymentFailure> failures = payrollPaymentFailureService.list(
                new LambdaQueryWrapper<PayrollPaymentFailure>()
                        .eq(PayrollPaymentFailure::getWorkflowId, workflowId)
        );
        assertThat(failures).singleElement().satisfies(failure -> {
            assertThat(failure.getStatus()).isEqualTo(PayrollPaymentFailureServiceImpl.STATUS_UNRESOLVED);
            assertThat(failure.getPayrollBatchId()).isEqualTo(20L);
            assertThat(failure.getBusinessKey()).isEqualTo("payroll_batch:20");
            assertThat(failure.getErrorMessage()).isEqualTo("payment creation failed");
        });

        payrollPaymentFailureService.remove(new LambdaQueryWrapper<PayrollPaymentFailure>()
                .eq(PayrollPaymentFailure::getWorkflowId, workflowId));
    }
}
