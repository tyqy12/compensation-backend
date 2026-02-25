package com.yiyundao.compensation.modules.app.service.impl;

import com.yiyundao.compensation.common.config.ExternalApiAuthProperties;
import com.yiyundao.compensation.modules.app.service.AppRateLimitAlertNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppRateLimitServiceImplTest {

    private AppRateLimitServiceImpl service;
    private TestAlertNotifier alertNotifier;

    @BeforeEach
    void setUp() {
        alertNotifier = new TestAlertNotifier();
        ExternalApiAuthProperties properties = new ExternalApiAuthProperties();
        properties.getRateLimit().setPerMinute(2);
        properties.getRateLimit().setAlertCooldownSeconds(60);

        service = new AppRateLimitServiceImpl(properties, alertNotifier, null);
    }

    @Test
    void fallbackRateLimiterTriggersAfterThreshold() {
        service.checkRate("appA", "127.0.0.1");
        service.checkRate("appA", "127.0.0.1");
        assertThatThrownBy(() -> service.checkRate("appA", "127.0.0.1"))
                .isInstanceOf(AppRateLimitServiceImpl.RateLimitExceededException.class);
        alertNotifier.assertNotified("appA", "127.0.0.1");
    }

    private static class TestAlertNotifier implements AppRateLimitAlertNotifier {
        private String notifiedClientId;
        private String notifiedIp;
        private long notifiedCount;

        @Override
        public void notifyRateLimit(String clientId, String clientIp, long currentCount) {
            this.notifiedClientId = clientId;
            this.notifiedIp = clientIp;
            this.notifiedCount = currentCount;
        }

        void assertNotified(String expectedClient, String expectedIp) {
            org.assertj.core.api.Assertions.assertThat(notifiedClientId).isEqualTo(expectedClient);
            org.assertj.core.api.Assertions.assertThat(notifiedIp).isEqualTo(expectedIp);
            org.assertj.core.api.Assertions.assertThat(notifiedCount).isGreaterThan(0);
        }
    }
}
