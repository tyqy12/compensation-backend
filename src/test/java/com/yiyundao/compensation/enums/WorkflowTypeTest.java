package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTypeTest {

    @Test
    void codeShouldBeThePersistedEnumValue() throws NoSuchFieldException {
        assertThat(WorkflowType.class.getDeclaredField("code").isAnnotationPresent(EnumValue.class)).isTrue();
        assertThat(WorkflowType.BATCH.getCode()).isEqualTo("BATCH");
        assertThat(WorkflowType.PAYROLL_DISTRIBUTION.getCode()).isEqualTo("PAYROLL_DISTRIBUTION");
        assertThat(WorkflowType.ADHOC.getCode()).isEqualTo("ADHOC");
        assertThat(WorkflowType.OFFLINE.getCode()).isEqualTo("OFFLINE");
        assertThat(WorkflowType.PERMISSION.getCode()).isEqualTo("PERMISSION");
        assertThat(WorkflowType.PAYROLL_DISPUTE.getCode()).isEqualTo("PAYROLL_DISPUTE");
    }
}
