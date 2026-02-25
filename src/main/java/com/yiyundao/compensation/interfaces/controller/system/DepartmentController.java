package com.yiyundao.compensation.interfaces.controller.system;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.dto.org.DepartmentNodeDto;
import com.yiyundao.compensation.modules.org.service.DepartmentService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/org/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping("/tree")
    @SecurityAnnotations.HasOrgReadPermission
    public ApiResponse<List<DepartmentNodeDto>> tree(@RequestParam String platform) {
        String p = normalizePlatform(platform);
        if (p == null) return ApiResponse.error(ErrorCode.PARAM_INVALID, "不支持的平台类型，支持：wechat|dingtalk|feishu");
        return ApiResponse.success(departmentService.getTree(p));
    }

    private String normalizePlatform(String platform) {
        if (platform == null) return null;
        String x = platform.trim().toLowerCase();
        return switch (x) {
            case "wechat", "wecom", "qywx", "wx" -> "wechat";
            case "dingtalk", "dingding", "dd" -> "dingtalk";
            case "feishu", "lark" -> "feishu";
            default -> null;
        };
    }
}
