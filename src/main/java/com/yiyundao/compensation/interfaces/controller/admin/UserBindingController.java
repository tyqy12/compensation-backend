package com.yiyundao.compensation.interfaces.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserBindingController {

    private final SysUserService sysUserService;

    @GetMapping("/{id}/platform-binding")
    public ApiResponse<BindingVO> get(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        if (user == null) return ApiResponse.error(404, "用户不存在");
        BindingVO vo = new BindingVO();
        vo.setPlatformType(user.getPlatformType());
        vo.setPlatformUserId(user.getPlatformUserId());
        vo.setUsername(user.getUsername());
        return ApiResponse.success(vo);
    }

    @PutMapping("/{id}/platform-binding")
    public ApiResponse<Void> bind(@PathVariable Long id, @RequestBody BindingForm form) {
        SysUser user = sysUserService.getById(id);
        if (user == null) return ApiResponse.error(404, "用户不存在");
        if (!StringUtils.hasText(form.getPlatformType()) || !StringUtils.hasText(form.getPlatformUserId())) {
            return ApiResponse.error(400, "平台类型与平台用户ID不能为空");
        }
        String pt = form.getPlatformType();
        if (!("wechat".equals(pt) || "dingtalk".equals(pt) || "feishu".equals(pt))) {
            return ApiResponse.error(400, "不支持的平台类型");
        }
        // 冲突检测：同一平台的该platformUserId不可重复绑定
        SysUser other = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPlatformType, pt)
                .eq(SysUser::getPlatformUserId, form.getPlatformUserId())
                .ne(SysUser::getId, id)
                .last("limit 1"));
        if (other != null) return ApiResponse.error(409, "该平台账号已绑定其他用户");
        user.setPlatformType(pt);
        user.setPlatformUserId(form.getPlatformUserId());
        sysUserService.updateById(user);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}/platform-binding")
    public ApiResponse<Void> unbind(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        if (user == null) return ApiResponse.error(404, "用户不存在");
        user.setPlatformType(null);
        user.setPlatformUserId(null);
        sysUserService.updateById(user);
        return ApiResponse.success(null);
    }

    @Data
    public static class BindingForm {
        @NotBlank
        private String platformType; // wechat/dingtalk/feishu
        @NotBlank
        private String platformUserId;
    }

    @Data
    public static class BindingVO {
        private String username;
        private String platformType;
        private String platformUserId;
    }
}

