package com.yiyundao.compensation.modules.rbac.service;

import com.yiyundao.compensation.infrastructure.dao.SysRoleResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.modules.rbac.service.impl.ResourceChangeListener;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ResourceChangeListenerTest {

    @Test
    void deleteEventShouldInvalidatePreCollectedAffectedUsers() {
        SysUserService sysUserService = mock(SysUserService.class);
        ResourceChangeListener listener = new ResourceChangeListener(
                sysUserService,
                mock(SysUserRoleMapper.class),
                mock(SysRoleResourceMapper.class),
                mock(SysUserResourceMapper.class)
        );

        listener.handleResourceChange(new ResourceChangeListener.ResourceChangeEvent(
                ResourceChangeListener.ResourceChangeEvent.ChangeType.DELETE,
                12L,
                Set.of(100L, 101L)
        ));

        verify(sysUserService).batchIncrementPermissionVersion(eq(Set.of(100L, 101L)));
    }
}
