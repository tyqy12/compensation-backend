package com.yiyundao.compensation.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollBatchStatusTest {

    @Test
    void approvedBatchShouldNotAllowRecomputeWhileRejectedCanBeCorrected() {
        assertThat(PayrollBatchStatus.APPROVED.canCompute()).isFalse();
        assertThat(PayrollBatchStatus.CONFIRMED.canCompute()).isFalse();
        assertThat(PayrollBatchStatus.REJECTED.canCompute()).isTrue();
    }

    @Test
    void shouldAllowOnlyDeclaredBatchStateTransitions() {
        assertThat(PayrollBatchStatus.DRAFT.canTransitionTo(PayrollBatchStatus.LOCKED)).isTrue();
        assertThat(PayrollBatchStatus.SUBMITTED.canTransitionTo(PayrollBatchStatus.APPROVED)).isTrue();
        assertThat(PayrollBatchStatus.PAY_FAILED.canTransitionTo(PayrollBatchStatus.PAY_PROCESSING)).isTrue();
        assertThat(PayrollBatchStatus.DRAFT.canTransitionTo(PayrollBatchStatus.PAID)).isFalse();
        assertThat(PayrollBatchStatus.PAID.canTransitionTo(PayrollBatchStatus.APPROVED)).isFalse();
        assertThat(PayrollBatchStatus.APPROVED.canTransitionTo(PayrollBatchStatus.APPROVED)).isTrue();
    }
}
