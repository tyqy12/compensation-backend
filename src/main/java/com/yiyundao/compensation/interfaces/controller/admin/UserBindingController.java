package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.modules.user.entity.ExternalIdentity;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.ExternalIdentityService;
import com.yiyundao.compensation.modules.user.service.LegacyPlatformFieldPolicy;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.modules.user.service.UserBindingService;
import com.yiyundao.compensation.modules.user.dto.UserPlatformBindingResult;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.yiyundao.compensation.security.SecurityAnnotations;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@SecurityAnnotations.IsAdmin
public class UserBindingController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    private final SysUserService sysUserService;
    private final UserBindingService userBindingService;
    private final EmployeeService employeeService;
    private final ExternalIdentityService externalIdentityService;
    private final LegacyPlatformFieldPolicy legacyPlatformFieldPolicy;

    // 用户绑定列表接口（分页）
    @GetMapping("/admin/user-bindings")
    public ApiResponse<Map<String, Object>> userBindings(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Boolean bound,
            HttpServletRequest request
    ) {
        int safeCurrent = safePage(current);
        int safePageSize = safeSize(pageSize);
        Page<SysUser> page = new Page<>(safeCurrent, safePageSize);
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();

        // 关键字搜索（用户名）
        String usernameKeyword = StringUtils.hasText(keyword) ? keyword : username;
        if (StringUtils.hasText(usernameKeyword)) {
            queryWrapper.like(SysUser::getUsername, usernameKeyword);
        }

        // 平台类型筛选
        String legacyPlatformType = request.getParameter("platformType");
        legacyPlatformFieldPolicy.handleLegacyInput(
                "admin_user_bindings_query",
                legacyPlatformType,
                null
        );
        String platformFilter = StringUtils.hasText(provider) ? provider : null;
        if (StringUtils.hasText(platformFilter)) {
            String normalizedPlatform = normalizePlatform(platformFilter);
            List<ExternalIdentity> identities = externalIdentityService.list(new LambdaQueryWrapper<ExternalIdentity>()
                            .select(ExternalIdentity::getUserId, ExternalIdentity::getEmployeeId)
                            .eq(ExternalIdentity::getProvider, normalizedPlatform)
                            .eq(ExternalIdentity::getStatus, ExternalIdentityService.STATUS_ACTIVE));
            List<Long> userIds = identities.stream()
                    .map(ExternalIdentity::getUserId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            List<Long> employeeIds = identities.stream()
                    .map(ExternalIdentity::getEmployeeId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (userIds.isEmpty() && employeeIds.isEmpty()) {
                return ApiResponse.success(emptyPageResponse(safeCurrent, safePageSize));
            }
            queryWrapper.and(wrapper -> {
                if (!userIds.isEmpty()) {
                    wrapper.in(SysUser::getId, userIds);
                }
                if (!employeeIds.isEmpty()) {
                    if (!userIds.isEmpty()) {
                        wrapper.or();
                    }
                    wrapper.in(SysUser::getEmployeeId, employeeIds);
                }
            });
        }

        if (bound != null) {
            applyBoundFilter(queryWrapper, bound);
        }

        // 排序
        queryWrapper.orderByDesc(SysUser::getCreateTime);

        Page<SysUser> result = sysUserService.page(page, queryWrapper);
        Map<Long, ExternalIdentity> userIdentityMap = loadPrimaryIdentityByUserIds(result.getRecords());
        Map<Long, ExternalIdentity> employeeIdentityMap = loadPrimaryIdentityByEmployeeIds(result.getRecords());
        Map<Long, Employee> employeeMap = loadEmployees(result.getRecords());

        Map<String, Object> response = new HashMap<>();
        response.put("records", result.getRecords().stream().map(user -> {
            UserBindingListVO vo = new UserBindingListVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setEmail(user.getEmail());
            vo.setPhone(user.getPhone());
            vo.setCreateTime(user.getCreateTime());
            vo.setUpdateTime(user.getUpdateTime());
            vo.setEmployeeId(user.getEmployeeId());
            Employee employee = user.getEmployeeId() != null ? employeeMap.get(user.getEmployeeId()) : null;
            if (employee != null) vo.setEmployeeName(employee.getName());

            ExternalIdentity identity = userIdentityMap.get(user.getId());
            if (identity == null && user.getEmployeeId() != null) {
                identity = employeeIdentityMap.get(user.getEmployeeId());
            }
            if (identity != null) {
                vo.setProvider(identity.getProvider());
                vo.setSubjectId(identity.getSubjectId());
            }
            vo.setBound(identity != null || user.getEmployeeId() != null);
            return vo;
        }).toList());
        response.put("total", result.getTotal());
        response.put("current", result.getCurrent());
        response.put("size", result.getSize());

        return ApiResponse.success(response);
    }

    private int safePage(int page) {
        return page < 1 ? 1 : page;
    }

    private int safeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private void applyBoundFilter(LambdaQueryWrapper<SysUser> queryWrapper, boolean bound) {
        String activeStatus = ExternalIdentityService.STATUS_ACTIVE.replace("'", "''");
        if (bound) {
            queryWrapper.and(wrapper -> wrapper
                    .isNotNull(SysUser::getEmployeeId)
                    .or()
                    .exists("SELECT 1 FROM external_identity ei"
                            + " WHERE ei.user_id = sys_user.id"
                            + " AND ei.status = '" + activeStatus + "'"
                            + " AND ei.deleted = 0"));
            return;
        }
        queryWrapper.isNull(SysUser::getEmployeeId)
                .notExists("SELECT 1 FROM external_identity ei"
                        + " WHERE ei.user_id = sys_user.id"
                        + " AND ei.status = '" + activeStatus + "'"
                        + " AND ei.deleted = 0");
    }

    @GetMapping("/admin/users/{id}/platform-binding")
    public ApiResponse<BindingVO> get(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        if (user == null) return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        BindingVO vo = new BindingVO();
        vo.setUsername(user.getUsername());
        vo.setEmployeeId(user.getEmployeeId());
        ExternalIdentity identity = externalIdentityService.findPrimaryByUserId(user.getId());
        if (user.getEmployeeId() != null) {
            Employee e = employeeService.getById(user.getEmployeeId());
            if (e != null) {
                vo.setEmployeeName(e.getName());
                if (identity == null) {
                    identity = externalIdentityService.findPrimaryByEmployeeId(e.getId());
                }
            }
        }
        if (identity != null) {
            vo.setProvider(identity.getProvider());
            vo.setSubjectId(identity.getSubjectId());
        }
        return ApiResponse.success(vo);
    }

    @PutMapping("/admin/users/{id}/platform-binding")
    public ApiResponse<Map<String, Object>> bind(@PathVariable Long id, @RequestBody BindingForm form) {
        SysUser user = sysUserService.getById(id);
        if (user == null) return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        legacyPlatformFieldPolicy.handleLegacyInput(
                "admin_user_binding_bind",
                form.getLegacyPlatformType(),
                form.getLegacyPlatformUserId()
        );
        if (!StringUtils.hasText(form.getProvider()) || !StringUtils.hasText(form.getSubjectId())) {
            return ApiResponse.error(ErrorCode.PARAM_MISSING, "平台类型与平台用户ID不能为空");
        }
        try {
            UserPlatformBindingResult result = userBindingService.bindPlatform(id, form.getProvider(), form.getSubjectId());
            Map<String, Object> data = new HashMap<>();
            data.put("status", result.status().name());
            data.put("workflowId", result.workflowId());
            return ApiResponse.<Map<String, Object>>builder()
                    .code(ErrorCode.SUCCESS.getCode())
                    .message(result.message())
                    .data(data)
                    .timestamp(LocalDateTime.now())
                    .build();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(ErrorCode.REQUEST_CONFLICT, e.getMessage());
        }
    }

    @DeleteMapping("/admin/users/{id}/platform-binding")
    public ApiResponse<Void> unbind(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        if (user == null) return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        userBindingService.unbindPlatform(id, true);
        return ApiResponse.success(null);
    }

    @Data
    public static class BindingForm {
        @NotBlank
        private String provider; // wechat/dingtalk/feishu
        @NotBlank
        private String subjectId;
        @JsonIgnore
        private String legacyPlatformType;
        @JsonIgnore
        private String legacyPlatformUserId;

        @JsonAnySetter
        public void captureLegacyPlatformFields(String key, Object value) {
            if (value == null || key == null) {
                return;
            }
            if ("platformType".equals(key)) {
                this.legacyPlatformType = String.valueOf(value);
                return;
            }
            if ("platformUserId".equals(key)) {
                this.legacyPlatformUserId = String.valueOf(value);
            }
        }
    }

    // 绑定到指定员工
    @PutMapping("/admin/users/{id}/bind-employee/{employeeId}")
    public ApiResponse<Void> bindEmployee(@PathVariable Long id, @PathVariable Long employeeId) {
        try {
            userBindingService.bindEmployee(id, employeeId);
            return ApiResponse.success(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(ErrorCode.REQUEST_CONFLICT, e.getMessage());
        }
    }

    // 一次性根据员工表回填创建账号并绑定（幂等）
    @PostMapping("/admin/users/provision-from-employees")
    public ApiResponse<Map<String, Object>> provisionFromEmployees() {
        List<Employee> all = employeeService.list();
        int createdOrUpdated = 0;
        for (Employee e : all) {
            userBindingService.ensureUserForEmployee(e);
            createdOrUpdated++;
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("processed", createdOrUpdated);
        return ApiResponse.success(resp);
    }

    @Data
    public static class BindingVO {
        private String username;
        private String provider;
        private String subjectId;
        private Long employeeId;
        private String employeeName;
    }

    @Data
    public static class UserBindingListVO {
        private Long id;
        private String username;
        private String provider;
        private String subjectId;
        private String email;
        private String phone;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Boolean bound; // 是否已绑定平台
        private Long employeeId;
        private String employeeName;
    }

    private String normalizePlatform(String platformType) {
        if (!StringUtils.hasText(platformType)) {
            return platformType;
        }
        String normalized = platformType.trim().toLowerCase();
        return switch (normalized) {
            case "wecom", "qywx", "wx" -> "wechat";
            case "dingding", "dd" -> "dingtalk";
            case "lark" -> "feishu";
            default -> normalized;
        };
    }

    private Map<String, Object> emptyPageResponse(int current, int pageSize) {
        Map<String, Object> response = new HashMap<>();
        response.put("records", List.of());
        response.put("total", 0L);
        response.put("current", current);
        response.put("size", pageSize);
        return response;
    }

    private Map<Long, ExternalIdentity> loadPrimaryIdentityByUserIds(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        Set<Long> userIds = users.stream()
                .map(SysUser::getId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<ExternalIdentity> identities = externalIdentityService.list(new LambdaQueryWrapper<ExternalIdentity>()
                .select(
                        ExternalIdentity::getId,
                        ExternalIdentity::getUserId,
                        ExternalIdentity::getProvider,
                        ExternalIdentity::getSubjectId,
                        ExternalIdentity::getLastSeenAt
                )
                .in(ExternalIdentity::getUserId, userIds)
                .eq(ExternalIdentity::getStatus, ExternalIdentityService.STATUS_ACTIVE)
                .orderByDesc(ExternalIdentity::getPrimaryFlag)
                .orderByDesc(ExternalIdentity::getLastSeenAt)
                .orderByDesc(ExternalIdentity::getId));
        Map<Long, ExternalIdentity> map = new HashMap<>();
        for (ExternalIdentity identity : identities) {
            if (identity.getUserId() != null) {
                map.putIfAbsent(identity.getUserId(), identity);
            }
        }
        return map;
    }

    private Map<Long, ExternalIdentity> loadPrimaryIdentityByEmployeeIds(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        Set<Long> employeeIds = users.stream()
                .map(SysUser::getEmployeeId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        List<ExternalIdentity> identities = externalIdentityService.list(new LambdaQueryWrapper<ExternalIdentity>()
                .select(
                        ExternalIdentity::getId,
                        ExternalIdentity::getEmployeeId,
                        ExternalIdentity::getProvider,
                        ExternalIdentity::getSubjectId,
                        ExternalIdentity::getLastSeenAt
                )
                .in(ExternalIdentity::getEmployeeId, employeeIds)
                .eq(ExternalIdentity::getStatus, ExternalIdentityService.STATUS_ACTIVE)
                .orderByDesc(ExternalIdentity::getPrimaryFlag)
                .orderByDesc(ExternalIdentity::getLastSeenAt)
                .orderByDesc(ExternalIdentity::getId));
        Map<Long, ExternalIdentity> map = new HashMap<>();
        for (ExternalIdentity identity : identities) {
            if (identity.getEmployeeId() != null) {
                map.putIfAbsent(identity.getEmployeeId(), identity);
            }
        }
        return map;
    }

    private Map<Long, Employee> loadEmployees(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        Set<Long> employeeIds = new HashSet<>();
        for (SysUser user : users) {
            if (user.getEmployeeId() != null) {
                employeeIds.add(user.getEmployeeId());
            }
        }
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        List<Employee> employees = employeeService.listByIds(new ArrayList<>(employeeIds));
        Map<Long, Employee> employeeMap = new HashMap<>();
        for (Employee employee : employees) {
            employeeMap.put(employee.getId(), employee);
        }
        return employeeMap;
    }
}
