package com.yiyundao.compensation.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementAccountPlaintextGuardTest {

    @Test
    void isRecognizedPlainAccountShouldAcceptSupportedLegacyPlainAccounts() {
        assertThat(SettlementAccountPlaintextGuard.isRecognizedPlainAccount("13800000000")).isTrue();
        assertThat(SettlementAccountPlaintextGuard.isRecognizedPlainAccount("payee@example.com")).isTrue();
        assertThat(SettlementAccountPlaintextGuard.isRecognizedPlainAccount("6222 0000 0000 0000")).isTrue();
    }

    @Test
    void isRecognizedPlainAccountShouldRejectCiphertextLikeValues() {
        assertThat(SettlementAccountPlaintextGuard.isRecognizedPlainAccount("ENC_BANK_VALUE")).isFalse();
        assertThat(SettlementAccountPlaintextGuard.isRecognizedPlainAccount("4Krc9uN1xLZMhT==")).isFalse();
        assertThat(SettlementAccountPlaintextGuard.isRecognizedPlainAccount("")).isFalse();
    }
}
