package com.yiyundao.compensation.interfaces.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.dto.rbac.ResourceDto;
import com.yiyundao.compensation.infrastructure.dao.SysRoleResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserResourceMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.common.exception.BusinessException;
import com.yiyundao.compensation.modules.rbac.service.ResourceService;
import com.yiyundao.compensation.modules.rbac.service.impl.ResourceChangeListener.ResourceChangeEvent;
import com.yiyundao.compensation.modules.rbac.entity.SysResource;
import com.yiyundao.compensation.modules.rbac.entity.SysRoleResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserResource;
import com.yiyundao.compensation.modules.rbac.entity.SysUserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminResourceV2ControllerTest {

    private ResourceService resourceService;
    private ObjectMapper objectMapper;
    private ApplicationEventPublisher eventPublisher;
    private SysRoleResourceMapper roleResourceMapper;
    private SysUserResourceMapper userResourceMapper;
    private SysUserRoleMapper userRoleMapper;
    private AdminResourceV2Controller controller;

    @BeforeEach
    void setUp() {
        resourceService = mock(ResourceService.class);
        objectMapper = new ObjectMapper();
        eventPublisher = mock(ApplicationEventPublisher.class);
        roleResourceMapper = mock(SysRoleResourceMapper.class);
        userResourceMapper = mock(SysUserResourceMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        controller = new AdminResourceV2Controller(
                resourceService,
                objectMapper,
                eventPublisher,
                roleResourceMapper,
                userResourceMapper,
                userRoleMapper
        );
    }

    @Test
    void updateShouldReturnNotFoundWhenResourceMissing() {
        when(resourceService.getById(10L)).thenReturn(null);

        ApiResponse<ResourceDto> response = controller.update(10L, new ResourceDto());

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("资源不存在");
        verify(resourceService, never()).updateById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteShouldReturnNotFoundWhenResourceMissing() {
        when(resourceService.getById(11L)).thenReturn(null);

        ApiResponse<Void> response = controller.delete(11L);

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("资源不存在");
        verify(resourceService, never()).removeById(11L);
    }

    @Test
    void deleteShouldRunInTransaction() throws Exception {
        Transactional transactional = AdminResourceV2Controller.class
                .getMethod("delete", Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(Arrays.asList(transactional.rollbackFor())).contains(Exception.class);
    }

    @Test
    void importShouldRunInTransaction() throws Exception {
        Transactional transactional = AdminResourceV2Controller.class
                .getMethod("importJson", List.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(Arrays.asList(transactional.rollbackFor())).contains(Exception.class);
    }

    @Test
    void importShouldRejectDuplicateCodesBeforeWriting() {
        ResourceDto first = resourceDto("admin.users");
        ResourceDto second = resourceDto("admin.users");

        assertThatThrownBy(() -> controller.importJson(List.of(first, second)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("资源编码重复");

        verify(resourceService, never()).save(any());
        verify(resourceService, never()).updateById(any());
    }

    @Test
    void importShouldRejectUnknownParentCodeBeforeWritingChild() {
        ResourceDto child = resourceDto("admin.users.list");
        child.setParentCode("admin.missing");

        assertThatThrownBy(() -> controller.importJson(List.of(child)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("父资源不存在或存在循环引用");

        verify(resourceService, never()).save(any());
        verify(resourceService, never()).updateById(any());
    }

    @Test
    void importShouldCreateParentThenResolveChildParentCodeAtomically() {
        ResourceDto parent = resourceDto("admin.users");
        ResourceDto child = resourceDto("admin.users.list");
        child.setParentCode("admin.users");
        when(resourceService.save(any())).thenAnswer(invocation -> {
            SysResource resource = invocation.getArgument(0);
            resource.setId(resource.getCode().equals("admin.users") ? 20L : 21L);
            return true;
        });

        ApiResponse<Map<String, Object>> response = controller.importJson(List.of(child, parent));

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).containsEntry("created", 2).containsEntry("updated", 0);
        var resourceCaptor = forClass(SysResource.class);
        verify(resourceService, org.mockito.Mockito.times(2)).save(resourceCaptor.capture());
        assertThat(resourceCaptor.getAllValues().get(0).getCode()).isEqualTo("admin.users");
        assertThat(resourceCaptor.getAllValues().get(1).getParentId()).isEqualTo(20L);
    }

    @Test
    void importShouldResolveMultiLevelParentCodeRegardlessOfRequestOrder() {
        ResourceDto root = resourceDto("admin");
        ResourceDto parent = resourceDto("admin.users");
        parent.setParentCode("admin");
        ResourceDto child = resourceDto("admin.users.list");
        child.setParentCode("admin.users");
        when(resourceService.save(any())).thenAnswer(invocation -> {
            SysResource resource = invocation.getArgument(0);
            resource.setId(switch (resource.getCode()) {
                case "admin" -> 10L;
                case "admin.users" -> 20L;
                default -> 30L;
            });
            return true;
        });

        ApiResponse<Map<String, Object>> response = controller.importJson(List.of(child, parent, root));

        assertThat(response.getCode()).isZero();
        var resourceCaptor = forClass(SysResource.class);
        verify(resourceService, org.mockito.Mockito.times(3)).save(resourceCaptor.capture());
        assertThat(resourceCaptor.getAllValues())
                .extracting(SysResource::getCode)
                .containsExactly("admin", "admin.users", "admin.users.list");
        assertThat(resourceCaptor.getAllValues().get(1).getParentId()).isEqualTo(10L);
        assertThat(resourceCaptor.getAllValues().get(2).getParentId()).isEqualTo(20L);
    }

    @Test
    void deleteShouldPublishPreDeleteAffectedUsersForPermissionCacheInvalidation() {
        SysResource resource = new SysResource();
        resource.setId(12L);

        SysUserResource directGrant = new SysUserResource();
        directGrant.setUserId(100L);
        SysRoleResource roleGrant = new SysRoleResource();
        roleGrant.setRoleId(200L);
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(101L);

        when(resourceService.getById(12L)).thenReturn(resource);
        when(resourceService.count(any())).thenReturn(0L);
        when(userResourceMapper.selectList(any())).thenReturn(List.of(directGrant));
        when(roleResourceMapper.selectList(any())).thenReturn(List.of(roleGrant));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole));

        ApiResponse<Void> response = controller.delete(12L);

        assertThat(response.getCode()).isZero();
        var eventCaptor = forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ResourceChangeEvent event = (ResourceChangeEvent) eventCaptor.getValue();
        assertThat(event.changeType()).isEqualTo(ResourceChangeEvent.ChangeType.DELETE);
        assertThat(event.resourceId()).isEqualTo(12L);
        assertThat(event.affectedUserIds()).containsExactlyInAnyOrder(100L, 101L);
        verify(roleResourceMapper).delete(argThat(wrapper -> wrapper != null));
        verify(userResourceMapper).delete(argThat(wrapper -> wrapper != null));
        verify(resourceService).removeById(12L);
    }

    private ResourceDto resourceDto(String code) {
        ResourceDto dto = new ResourceDto();
        dto.setType("API");
        dto.setCode(code);
        dto.setName(code);
        dto.setPath("/api/" + code.replace('.', '/'));
        dto.setMeta(Map.of("method", "GET"));
        return dto;
    }
}
