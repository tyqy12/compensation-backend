package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.infrastructure.dao.SysRoleMapper;
import com.yiyundao.compensation.infrastructure.dao.SysUserRoleMapper;
import com.yiyundao.compensation.interfaces.dto.admin.UserAggregateDto;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.rbac.entity.SysRole;
import com.yiyundao.compensation.modules.rbac.entity.SysUserRole;
import com.yiyundao.compensation.modules.rbac.service.ResourceService;
import com.yiyundao.compensation.modules.rbac.service.UserRoleService;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRoleControllerTest {

    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private ResourceService resourceService;
    @Mock
    private UserRoleService userRoleService;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private ExternalIdentityService externalIdentityService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        initTableInfo(configuration, Employee.class);
        initTableInfo(configuration, SysUser.class);
        initTableInfo(configuration, ExternalIdentity.class);
        initTableInfo(configuration, SysUserRole.class);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void userAggregateSearchShouldResolveEmployeeMatchesWithoutRawInSql() {
        AdminRoleController controller = new AdminRoleController(
                userRoleMapper,
                roleMapper,
                resourceService,
                userRoleService,
                sysUserService,
                employeeService,
                externalIdentityService,
                new ObjectMapper()
        );

        Employee employee = new Employee();
        employee.setId(99L);
        employee.setEmployeeId("E-099");
        employee.setName("Alice");
        when(employeeService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(employee));
        when(employeeService.listByIds(anyCollection())).thenReturn(List.of(employee));

        SysUser user = new SysUser();
        user.setId(10L);
        user.setUsername("alice");
        user.setEmployeeId(99L);
        Page<SysUser> userPage = new Page<>(1, 20, 1);
        userPage.setRecords(List.of(user));
        when(sysUserService.page(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(userPage);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(10L);
        userRole.setRoleId(5L);
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userRole));

        SysRole role = new SysRole();
        role.setId(5L);
        role.setCode("ROLE_EMPLOYEE");
        when(roleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(role));

        when(externalIdentityService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ApiResponse<Map<String, Object>> response = controller.userAggregateSearch("Alice", 1, 20);

        assertThat(response.getData()).isNotNull();
        assertThat((List<UserAggregateDto>) response.getData().get("records"))
                .singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getUserId()).isEqualTo(10L);
                    assertThat(dto.getEmployeeId()).isEqualTo(99L);
                    assertThat(dto.getEmployeeName()).isEqualTo("Alice");
                    assertThat(dto.getRoles()).isEqualTo("ROLE_EMPLOYEE");
                });

        ArgumentCaptor<LambdaQueryWrapper> userQueryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sysUserService).page(any(Page.class), userQueryCaptor.capture());
        String sqlSegment = userQueryCaptor.getValue().getCustomSqlSegment();
        assertThat(sqlSegment).doesNotContain("SELECT id FROM employee");
        assertThat(sqlSegment).doesNotContain("inSql");

        verify(employeeService).list(any(LambdaQueryWrapper.class));
    }

    @Test
    void setUserRolesShouldUseAuthenticatedOperatorId() {
        AdminRoleController controller = new AdminRoleController(
                userRoleMapper,
                roleMapper,
                resourceService,
                userRoleService,
                sysUserService,
                employeeService,
                externalIdentityService,
                new ObjectMapper()
        );
        SysUser currentUser = new SysUser();
        currentUser.setId(88L);
        when(sysUserService.findByUsername("admin")).thenReturn(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a")
        );

        AdminRoleController.SetUserRolesRequest request = new AdminRoleController.SetUserRolesRequest();
        request.setRoleIds(List.of(2L, 3L));

        controller.setUserRoles(10L, request);

        verify(userRoleService).replaceUserRoles(10L, request.getRoleIds(), 88L);
    }

    private static void initTableInfo(MybatisConfiguration configuration, Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, entityType.getName());
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
