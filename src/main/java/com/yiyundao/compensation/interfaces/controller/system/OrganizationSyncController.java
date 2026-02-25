package com.yiyundao.compensation.interfaces.controller.system;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.dto.system.OrgSyncHistoryItemDto;
import com.yiyundao.compensation.modules.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import com.yiyundao.compensation.service.OrganizationSyncService;
import com.yiyundao.compensation.modules.system.service.OrgSyncTaskService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.modules.audit.entity.AuditLog;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/system/org")
@RequiredArgsConstructor
public class OrganizationSyncController {

    private final OrganizationSyncService organizationSyncService;
    private final AuditLogService auditLogService;
    private final OrgSyncTaskService orgSyncTaskService;
    private final com.yiyundao.compensation.modules.employee.service.EmployeeDepartmentService employeeDepartmentService;

    // 触发单平台或全平台同步，platform=wechat|dingtalk|feishu|all
    @PostMapping("/sync")
    @SecurityAnnotations.HasOrgSyncPermission
    public ApiResponse<?> sync(@RequestParam(required = false, defaultValue = "all") String platform, HttpServletRequest request) {
        // 自动同步已取消：提示使用 fetch + import 手动流程
        return ApiResponse.error(ErrorCode.BUSINESS_ERROR, "自动同步已取消，请使用 /system/org/fetch 获取预览后手动 /system/org/import 导入");
    }

    // 查看支持的平台列表
    @GetMapping("/platforms")
    @SecurityAnnotations.IsAuthenticated
    public ApiResponse<List<String>> platforms() {
        return ApiResponse.success(organizationSyncService.getSupportedPlatforms());
    }

    // 检查连接
    @GetMapping("/check")
    @SecurityAnnotations.HasOrgReadPermission
    public ApiResponse<Boolean> check(@RequestParam(required = false, defaultValue = "all") String platform, HttpServletRequest request) {
        platform = normalizePlatform(platform);
        if (platform == null) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "不支持的平台类型，支持：wechat|dingtalk|feishu|all");
        }
        long begin = System.currentTimeMillis();
        boolean ok = "all".equals(platform) ? true : organizationSyncService.checkPlatformConnection(platform);
        auditLogService.record("检查组织同步", request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), request.getHeader("User-Agent"),
                "ORG", platform, request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null,
                "platform=" + platform, ok ? "OK" : "FAILED", null, System.currentTimeMillis() - begin);
        return ApiResponse.success(ok);
    }

    // 异步任务：创建并返回任务ID
    @PostMapping("/sync-async")
    @SecurityAnnotations.HasOrgSyncPermission
    public ApiResponse<String> syncAsync(@RequestParam(required = false, defaultValue = "all") String platform, HttpServletRequest request) {
        return ApiResponse.error(ErrorCode.BUSINESS_ERROR, "自动同步任务已下线，请使用手动fetch/import流程");
    }

    // 手动获取预览数据，不落库
    @PostMapping("/fetch")
    @SecurityAnnotations.HasOrgSyncPermission
    public ApiResponse<com.yiyundao.compensation.interfaces.dto.org.OrgFetchPreviewResponse> fetch(
            @RequestParam(required = false) String platform,
            @RequestBody(required = false) com.yiyundao.compensation.interfaces.dto.org.OrgFetchRequest body
    ) {
        String pf = platform;
        if ((pf == null || pf.isBlank()) && body != null) {
            pf = body.getPlatform();
        }
        String p = normalizePlatform(pf);
        if (p == null || "all".equals(p)) return ApiResponse.error(ErrorCode.PARAM_INVALID, "请指定单个平台：wechat|dingtalk|feishu");
        java.util.List<com.yiyundao.compensation.modules.employee.entity.Employee> raw = organizationSyncService.fetchPreview(p);
        // 聚合：按平台用户聚合多部门
        java.util.Map<String, com.yiyundao.compensation.interfaces.dto.org.EmployeePreviewDto> map = new java.util.LinkedHashMap<>();
        for (com.yiyundao.compensation.modules.employee.entity.Employee e : raw) {
            String key = e.getPlatformType() + ":" + e.getPlatformUserId();
            com.yiyundao.compensation.interfaces.dto.org.EmployeePreviewDto dto = map.get(key);
            if (dto == null) {
                dto = new com.yiyundao.compensation.interfaces.dto.org.EmployeePreviewDto();
                dto.setGroupKey(key);
                dto.setPlatformType(e.getPlatformType());
                dto.setPlatformUserId(e.getPlatformUserId());
                dto.setEmployeeId(null); // 工号由前端填写
                dto.setName(e.getName());
                dto.setPhone(e.getPhone());
                dto.setEmail(e.getEmail());
                dto.setDepartments(new java.util.ArrayList<>());
                dto.setPosition(e.getPosition());
                dto.setEmploymentType(e.getEmploymentType());
                map.put(key, dto);
            }
            if (e.getDepartment() != null && !e.getDepartment().isBlank()) {
                java.util.List<String> depts = dto.getDepartments();
                String[] parts = e.getDepartment().split(",");
                for (String part : parts) {
                    String name = part.trim();
                    if (!name.isEmpty() && depts.stream().noneMatch(d -> d.equals(name))) {
                        depts.add(name);
                    }
                }
                if (dto.getDepartment() == null && !depts.isEmpty()) dto.setDepartment(depts.get(0));
            }
        }
        com.yiyundao.compensation.interfaces.dto.org.OrgFetchPreviewResponse resp = new com.yiyundao.compensation.interfaces.dto.org.OrgFetchPreviewResponse();
        resp.setPlatformType(p);
        resp.setTotalEmployees(map.size());
        resp.setEmployees(new java.util.ArrayList<>(map.values()));
        return ApiResponse.success(resp);
    }

    // 新增：部门树预览（不落库）
    @PostMapping("/fetch-tree")
    @SecurityAnnotations.HasOrgReadPermission
    public ApiResponse<com.yiyundao.compensation.interfaces.dto.org.OrgDeptTreeResponse> fetchTree(
            @RequestParam(required = false) String platform,
            @RequestBody(required = false) com.yiyundao.compensation.interfaces.dto.org.OrgFetchRequest body
    ) {
        String pf = platform;
        if ((pf == null || pf.isBlank()) && body != null) pf = body.getPlatform();
        String p = normalizePlatform(pf);
        if (p == null || "all".equals(p)) return ApiResponse.error(ErrorCode.PARAM_INVALID, "请指定单个平台：wechat|dingtalk|feishu");
        java.util.List<com.yiyundao.compensation.interfaces.dto.org.OrgDeptNodeDto> roots = organizationSyncService.fetchDepartmentTree(p);
        com.yiyundao.compensation.interfaces.dto.org.OrgDeptTreeResponse resp = new com.yiyundao.compensation.interfaces.dto.org.OrgDeptTreeResponse();
        resp.setPlatformType(p);
        resp.setRoots(roots);
        return ApiResponse.success(resp);
    }

    // 手动导入：前端可编辑后提交本接口进行入库
    @PostMapping("/import")
    @SecurityAnnotations.HasOrgSyncPermission
    public ApiResponse<java.util.Map<String, Object>> importEmployees(@RequestBody com.yiyundao.compensation.interfaces.dto.org.OrgImportRequest req) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) return ApiResponse.error(ErrorCode.PARAM_INVALID, "导入内容为空");
        int ok = 0; int fail = 0;
        java.util.List<String> errors = new java.util.ArrayList<>();
        for (com.yiyundao.compensation.interfaces.dto.org.OrgImportRequest.EmployeeItem it : req.getItems()) {
            try {
                com.yiyundao.compensation.modules.employee.entity.Employee e = new com.yiyundao.compensation.modules.employee.entity.Employee();
                e.setEmployeeId(it.getEmployeeId());
                e.setName(it.getName());
                e.setPhone(it.getPhone());
                e.setEmail(it.getEmail());
                // 展示用主部门+多部门
                if (it.getDepartments() != null && !it.getDepartments().isEmpty()) {
                    e.setDepartment(String.join(",", it.getDepartments()));
                }
                e.setPosition(it.getPosition());
                e.setEmploymentType(it.getEmploymentType());
                e.setPlatformUserId(it.getPlatformUserId());
                e.setPlatformType(it.getPlatformType());
                e.setStatus(it.getStatus());
                e.setOffline(it.getOffline());
                e.setManagerId(it.getManagerId());
                e.setBankAccount(it.getBankAccount());
                e.setBankName(it.getBankName());
                e.setHireDate(it.getHireDate());
                com.yiyundao.compensation.modules.employee.entity.Employee saved = organizationSyncService.importOne(e, it.getUsername());
                // 维护多部门关联
                if (it.getDepartments() != null && !it.getDepartments().isEmpty()) {
                    employeeDepartmentService.replaceDepartments(saved.getId(), saved.getPlatformType(), it.getDepartments());
                }
                if (saved != null) ok++; else fail++;
            } catch (Exception ex) {
                fail++;
                errors.add(ex.getMessage());
            }
        }
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", ok);
        result.put("failed", fail);
        result.put("errors", errors);
        return ApiResponse.success(result);
    }

    private String normalizePlatform(String platform) {
        if (platform == null) return null;
        String p = platform.trim().toLowerCase();
        switch (p) {
            case "wechat":
            case "wecom":
            case "qywx":
            case "wx":
                return "wechat";
            case "dingtalk":
            case "dingding":
            case "dd":
                return "dingtalk";
            case "feishu":
            case "lark":
                return "feishu";
            case "all":
            case "*":
                return "all";
            default:
                return null;
        }
    }

    // 异步任务查询
    @GetMapping("/sync-task/{id}")
    @SecurityAnnotations.HasOrgReadPermission
    public ApiResponse<OrgSyncTaskService.TaskInfo> task(@PathVariable String id) {
        return ApiResponse.success(orgSyncTaskService.get(id));
    }

    // 历史记录（审计日志中 business_type=ORG）
    @GetMapping("/history")
    @SecurityAnnotations.HasOrgReadPermission
    public ApiResponse<Map<String, Object>> history(@RequestParam(defaultValue = "1") int current,
                                                    @RequestParam(defaultValue = "10") int pageSize,
                                                    @RequestParam(required = false) String platform,
                                                    @RequestParam(required = false) String operation) {
        String normalized = platform == null ? null : normalizePlatform(platform);
        Page<AuditLog> page = new Page<>(current, pageSize);
        LambdaQueryWrapper<AuditLog> w = new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getBusinessType, "ORG")
                .orderByDesc(AuditLog::getCreateTime);
        if (normalized != null && !"all".equals(normalized)) {
            w.eq(AuditLog::getBusinessKey, normalized);
        }
        if (operation != null && !operation.isBlank()) {
            w.eq(AuditLog::getOperation, operation);
        }
        Page<AuditLog> result = auditLogService.page(page, w);

        List<OrgSyncHistoryItemDto> records = result.getRecords().stream().map(log -> {
            OrgSyncHistoryItemDto dto = new OrgSyncHistoryItemDto();
            dto.setId(log.getId());
            dto.setOperation(log.getOperation());
            dto.setPlatform(log.getBusinessKey());
            dto.setResult(log.getResponseResult());
            dto.setUsername(log.getUsername());
            dto.setRequestUrl(log.getRequestUrl());
            dto.setRequestIp(log.getRequestIp());
            dto.setExecutionTime(log.getExecutionTime());
            dto.setCreateTime(log.getCreateTime());
            return dto;
        }).toList();

        Map<String, Object> resp = new HashMap<>();
        resp.put("records", records);
        resp.put("total", result.getTotal());
        resp.put("current", result.getCurrent());
        resp.put("size", result.getSize());
        return ApiResponse.success(resp);
    }
}
