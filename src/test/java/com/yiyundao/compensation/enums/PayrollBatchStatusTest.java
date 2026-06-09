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
}
