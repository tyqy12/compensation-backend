package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.modules.user.service.UserBindingService;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.employee.entity.Employee;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.yiyundao.compensation.security.SecurityAnnotations;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@SecurityAnnotations.IsAdmin
public class UserBindingController {

    private final SysUserService sysUserService;
    private final UserBindingService userBindingService;
    private final EmployeeService employeeService;

    // 用户绑定列表接口（分页）
    @GetMapping("/admin/user-bindings")
    public ApiResponse<Map<String, Object>> userBindings(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String platformType
    ) {
        Page<SysUser> page = new Page<>(current, pageSize);
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();

        // 关键字搜索（用户名）
        if (StringUtils.hasText(keyword)) {
            queryWrapper.like(SysUser::getUsername, keyword);
        }

        // 平台类型筛选
        if (StringUtils.hasText(platformType)) {
            queryWrapper.eq(SysUser::getPlatformType, platformType);
        }

        // 排序
        queryWrapper.orderByDesc(SysUser::getCreateTime);

        Page<SysUser> result = sysUserService.page(page, queryWrapper);

        Map<String, Object> response = new HashMap<>();
        response.put("records", result.getRecords().stream().map(user -> {
            UserBindingListVO vo = new UserBindingListVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setPlatformType(user.getPlatformType());
            vo.setPlatformUserId(user.getPlatformUserId());
            vo.setEmail(user.getEmail());
            vo.setPhone(user.getPhone());
            vo.setCreateTime(user.getCreateTime());
            vo.setUpdateTime(user.getUpdateTime());
            vo.setBound((StringUtils.hasText(user.getPlatformType()) && StringUtils.hasText(user.getPlatformUserId())) || user.getEmployeeId() != null);
            vo.setEmployeeId(user.getEmployeeId());
            if (user.getEmployeeId() != null) {
                Employee e = employeeService.getById(user.getEmployeeId());
                if (e != null) {
                    vo.setEmployeeName(e.getName());
                    if (!StringUtils.hasText(vo.getPlatformType())) vo.setPlatformType(e.getPlatformType());
                    if (!StringUtils.hasText(vo.getPlatformUserId())) vo.setPlatformUserId(e.getPlatformUserId());
                }
            }
            return vo;
        }).toList());
        response.put("total", result.getTotal());
        response.put("current", result.getCurrent());
        response.put("size", result.getSize());

        return ApiResponse.success(response);
    }

    @GetMapping("/admin/users/{id}/platform-binding")
    public ApiResponse<BindingVO> get(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        if (user == null) return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        BindingVO vo = new BindingVO();
        vo.setPlatformType(user.getPlatformType());
        vo.setPlatformUserId(user.getPlatformUserId());
        vo.setUsername(user.getUsername());
        vo.setEmployeeId(user.getEmployeeId());
        if (user.getEmployeeId() != null) {
            Employee e = employeeService.getById(user.getEmployeeId());
            if (e != null) vo.setEmployeeName(e.getName());
        }
        return ApiResponse.success(vo);
    }

    @PutMapping("/admin/users/{id}/platform-binding")
    public ApiResponse<Void> bind(@PathVariable Long id, @RequestBody BindingForm form) {
        SysUser user = sysUserService.getById(id);
        if (user == null) return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        if (!StringUtils.hasText(form.getPlatformType()) || !StringUtils.hasText(form.getPlatformUserId())) {
            return ApiResponse.error(ErrorCode.PARAM_MISSING, "平台类型与平台用户ID不能为空");
        }
        try {
            userBindingService.bindPlatform(id, form.getPlatformType(), form.getPlatformUserId());
            return ApiResponse.success(null);
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
        private String platformType; // wechat/dingtalk/feishu
        @NotBlank
        private String platformUserId;
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
        private String platformType;
        private String platformUserId;
        private Long employeeId;
        private String employeeName;
    }

    @Data
    public static class UserBindingListVO {
        private Long id;
        private String username;
        private String platformType;
        private String platformUserId;
        private String email;
        private String phone;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Boolean bound; // 是否已绑定平台
        private Long employeeId;
        private String employeeName;
    }
}
