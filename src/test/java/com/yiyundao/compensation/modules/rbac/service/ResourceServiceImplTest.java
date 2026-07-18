package com.yiyundao.compensation.modules.rbac.service;

import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.service.impl.ResourceServiceImpl;
import com.yiyundao.compensation.security.DatabasePermissionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceServiceImplTest {

    @Test
    void userResourcesAndActionsShouldComeFromDatabasePermissionBundle() {
        DatabasePermissionService permissionService = mock(DatabasePermissionService.class);
        SysResource resource = new SysResource();
        resource.setId(3002L);
        resource.setCode("api.enabled.role.resource");
        resource.setStatus("enabled");

        when(permissionService.getUserBundle(1002L)).thenReturn(
                new DatabasePermissionService.PermissionBundle(
                        List.of(resource),
                        Map.of(3002L, List.of("read", "write"))));

        ResourceServiceImpl service = new ResourceServiceImpl(permissionService);

        assertThat(service.getUserResources(1002L)).containsExactly(resource);
        assertThat(service.getUserActions(1002L))
                .containsEntry(3002L, List.of("read", "write"));
    }

    @Test
    void missingDatabaseBundleShouldExposeNoResources() {
        DatabasePermissionService permissionService = mock(DatabasePermissionService.class);
        when(permissionService.getUserBundle(1003L))
                .thenReturn(new DatabasePermissionService.PermissionBundle(List.of(), Map.of()));

        ResourceServiceImpl service = new ResourceServiceImpl(permissionService);

        assertThat(service.getUserResources(1003L)).isEmpty();
        assertThat(service.getUserActions(1003L)).isEmpty();
    }
}
