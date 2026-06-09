package com.yiyundao.compensation.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yiyundao.compensation.interfaces.dto.config.WechatConfigDto;
import com.yiyundao.compensation.modules.system.service.IntegrationConfigService;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class ExternalIdentityServiceImplTest {

    @Test
    void upsertPlatformIdentityShouldReactivateInactiveIdentityInsteadOfInsertingDuplicateSubject() {
        initTableInfo(ExternalIdentity.class);
        ExternalIdentity inactive = new ExternalIdentity();
        inactive.setId(3001L);
        inactive.setProvider("wechat");
        inactive.setTenantKey(ExternalIdentityService.DEFAULT_TENANT_KEY);
        inactive.setSubjectType(ExternalIdentityService.DEFAULT_SUBJECT_TYPE);
        inactive.setSubjectId("wx-inactive");
        inactive.setUserId(9001L);
        inactive.setEmployeeId(8001L);
        inactive.setStatus(ExternalIdentityService.STATUS_INACTIVE);
        inactive.setPrimaryFlag(false);

        TestableExternalIdentityService service = new TestableExternalIdentityService(inactive);

        service.upsertPlatformIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-inactive",
                2001L,
                1001L,
                "approval:7001",
                true
        );

        assertThat(service.saved).isNull();
        assertThat(service.updated).isSameAs(inactive);
        assertThat(inactive.getStatus()).isEqualTo(ExternalIdentityService.STATUS_ACTIVE);
        assertThat(inactive.getEmployeeId()).isEqualTo(2001L);
        assertThat(inactive.getUserId()).isEqualTo(1001L);
        assertThat(inactive.getPrimaryFlag()).isTrue();
        assertThat(inactive.getSource()).isEqualTo("approval:7001");
        assertThat(inactive.getUnboundAt()).isNull();
    }

    @Test
    void upsertPlatformIdentityShouldClearStaleUserWhenReactivatingForEmployeeWithoutUser() {
        initTableInfo(ExternalIdentity.class);
        ExternalIdentity inactive = new ExternalIdentity();
        inactive.setId(3002L);
        inactive.setProvider("wechat");
        inactive.setTenantKey(ExternalIdentityService.DEFAULT_TENANT_KEY);
        inactive.setSubjectType(ExternalIdentityService.DEFAULT_SUBJECT_TYPE);
        inactive.setSubjectId("wx-stale-user");
        inactive.setUserId(9002L);
        inactive.setEmployeeId(8002L);
        inactive.setStatus(ExternalIdentityService.STATUS_INACTIVE);
        inactive.setPrimaryFlag(true);

        TestableExternalIdentityService service = new TestableExternalIdentityService(inactive);

        service.upsertPlatformIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-stale-user",
                2002L,
                null,
                "approval:7002",
                true
        );

        assertThat(service.saved).isNull();
        assertThat(service.updated).isSameAs(inactive);
        assertThat(inactive.getStatus()).isEqualTo(ExternalIdentityService.STATUS_ACTIVE);
        assertThat(inactive.getEmployeeId()).isEqualTo(2002L);
        assertThat(inactive.getUserId()).isNull();
        assertThat(inactive.getUnboundAt()).isNull();
    }

    @Test
    void upsertPlatformIdentityShouldNotClearActiveUserWhenCallerDoesNotKnowUserId() {
        initTableInfo(ExternalIdentity.class);
        ExternalIdentity active = new ExternalIdentity();
        active.setId(3003L);
        active.setProvider("wechat");
        active.setTenantKey(ExternalIdentityService.DEFAULT_TENANT_KEY);
        active.setSubjectType(ExternalIdentityService.DEFAULT_SUBJECT_TYPE);
        active.setSubjectId("wx-active");
        active.setUserId(9003L);
        active.setEmployeeId(8003L);
        active.setStatus(ExternalIdentityService.STATUS_ACTIVE);
        active.setPrimaryFlag(true);

        TestableExternalIdentityService service = new TestableExternalIdentityService(active);

        service.upsertPlatformIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-active",
                8003L,
                null,
                "sync",
                true
        );

        assertThat(service.updated).isSameAs(active);
        assertThat(active.getUserId()).isEqualTo(9003L);
        assertThat(active.getEmployeeId()).isEqualTo(8003L);
    }

    @Test
    void findActiveIdentityShouldFallbackToHistoricalTenantWhenDefaultTenantWasReconfigured() {
        initTableInfo(ExternalIdentity.class);
        ExternalIdentity legacy = new ExternalIdentity();
        legacy.setId(3004L);
        legacy.setProvider("wechat");
        legacy.setTenantKey("old-corp");
        legacy.setSubjectType(ExternalIdentityService.DEFAULT_SUBJECT_TYPE);
        legacy.setSubjectId("wx-historical");
        legacy.setUserId(9004L);
        legacy.setEmployeeId(8004L);
        legacy.setStatus(ExternalIdentityService.STATUS_ACTIVE);
        legacy.setPrimaryFlag(true);

        IntegrationConfigService integrationConfigService = mock(IntegrationConfigService.class);
        WechatConfigDto wechatConfig = new WechatConfigDto();
        wechatConfig.setCorpId("new-corp");
        when(integrationConfigService.getWechatConfig()).thenReturn(wechatConfig);
        TestableExternalIdentityService service = new TestableExternalIdentityService(
                integrationConfigService,
                null,
                null,
                legacy
        );

        ExternalIdentity result = service.findActiveIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-historical"
        );

        assertThat(result).isSameAs(legacy);
    }

    @Test
    void upsertPlatformIdentityShouldReuseHistoricalTenantRecordInsteadOfInsertingDuplicate() {
        initTableInfo(ExternalIdentity.class);
        ExternalIdentity legacy = new ExternalIdentity();
        legacy.setId(3005L);
        legacy.setProvider("wechat");
        legacy.setTenantKey("old-corp");
        legacy.setSubjectType(ExternalIdentityService.DEFAULT_SUBJECT_TYPE);
        legacy.setSubjectId("wx-moved-tenant");
        legacy.setUserId(9005L);
        legacy.setEmployeeId(8005L);
        legacy.setStatus(ExternalIdentityService.STATUS_ACTIVE);
        legacy.setPrimaryFlag(true);

        IntegrationConfigService integrationConfigService = mock(IntegrationConfigService.class);
        WechatConfigDto wechatConfig = new WechatConfigDto();
        wechatConfig.setCorpId("new-corp");
        when(integrationConfigService.getWechatConfig()).thenReturn(wechatConfig);
        TestableExternalIdentityService service = new TestableExternalIdentityService(
                integrationConfigService,
                null,
                legacy
        );

        service.upsertPlatformIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-moved-tenant",
                8005L,
                9005L,
                "sync",
                true
        );

        assertThat(service.saved).isNull();
        assertThat(service.updated).isSameAs(legacy);
        assertThat(legacy.getTenantKey()).isEqualTo("new-corp");
        assertThat(service.demoteWrappers).hasSize(2);
    }

    @Test
    void upsertPlatformIdentityShouldDemoteOtherPrimaryIdentitiesWhenNewPrimaryCreated() {
        initTableInfo(ExternalIdentity.class);
        TestableExternalIdentityService service = new TestableExternalIdentityService(
                mock(IntegrationConfigService.class),
                null
        );

        service.upsertPlatformIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-new-primary",
                8006L,
                9006L,
                "manual",
                true
        );

        assertThat(service.saved).isNotNull();
        assertThat(service.saved.getPrimaryFlag()).isTrue();
        assertThat(service.demoteWrappers).hasSize(2);
        assertThat(service.demoteWrappers)
                .allSatisfy(wrapper -> assertThat(wrapper.getSqlSet()).contains("is_primary"));
    }

    @Test
    void upsertPlatformIdentityShouldNotDemoteOtherIdentitiesWhenNewIdentityIsNotPrimary() {
        initTableInfo(ExternalIdentity.class);
        TestableExternalIdentityService service = new TestableExternalIdentityService(
                mock(IntegrationConfigService.class),
                null
        );

        service.upsertPlatformIdentity(
                "wechat",
                ExternalIdentityService.DEFAULT_TENANT_KEY,
                ExternalIdentityService.DEFAULT_SUBJECT_TYPE,
                "wx-secondary",
                8007L,
                9007L,
                "manual",
                false
        );

        assertThat(service.saved).isNotNull();
        assertThat(service.saved.getPrimaryFlag()).isFalse();
        assertThat(service.demoteWrappers).isEmpty();
    }

    private static void initTableInfo(Class<?> entityType) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, entityType.getName());
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    private static class TestableExternalIdentityService extends ExternalIdentityServiceImpl {
        private final java.util.Queue<ExternalIdentity> queryResults;
        private ExternalIdentity saved;
        private ExternalIdentity updated;
        private final List<LambdaUpdateWrapper<?>> demoteWrappers = new ArrayList<>();

        private TestableExternalIdentityService(ExternalIdentity existing) {
            super(mock(IntegrationConfigService.class));
            this.queryResults = new java.util.LinkedList<>();
            this.queryResults.add(existing);
        }

        private TestableExternalIdentityService(IntegrationConfigService integrationConfigService,
                                                ExternalIdentity... queryResults) {
            super(integrationConfigService);
            this.queryResults = new java.util.LinkedList<>();
            if (queryResults != null) {
                for (ExternalIdentity result : queryResults) {
                    this.queryResults.add(result);
                }
            }
        }

        @Override
        public ExternalIdentity getOne(Wrapper<ExternalIdentity> queryWrapper) {
            return queryResults.isEmpty() ? null : queryResults.remove();
        }

        @Override
        public boolean save(ExternalIdentity entity) {
            this.saved = entity;
            return true;
        }

        @Override
        public boolean updateById(ExternalIdentity entity) {
            this.updated = entity;
            return true;
        }

        @Override
        public boolean update(Wrapper<ExternalIdentity> updateWrapper) {
            if (updateWrapper instanceof LambdaUpdateWrapper<?> lambdaUpdateWrapper) {
                this.demoteWrappers.add(lambdaUpdateWrapper);
            }
            return true;
        }
    }
}
