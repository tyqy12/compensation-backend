package com.yiyundao.compensation.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalStatusTest {

    @Test
    void codeShouldBeThePersistedEnumValue() throws NoSuchFieldException {
        assertThat(ApprovalStatus.class.getDeclaredField("code").isAnnotationPresent(EnumValue.class)).isTrue();
        assertThat(ApprovalStatus.PENDING.getCode()).isEqualTo("pending");
        assertThat(ApprovalStatus.APPROVED.getCode()).isEqualTo("approved");
        assertThat(ApprovalStatus.REJECTED.getCode()).isEqualTo("rejected");
        assertThat(ApprovalStatus.CANCELLED.getCode()).isEqualTo("cancelled");
        assertThat(ApprovalStatus.SKIPPED.getCode()).isEqualTo("skipped");
    }
}
