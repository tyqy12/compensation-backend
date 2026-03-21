package com.yiyundao.compensation.interfaces.controller.employee;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.common.response.PageResponse;
import com.yiyundao.compensation.interfaces.dto.employee.EmployeeSelfContactUpdateRequest;
import com.yiyundao.compensation.interfaces.dto.employee.EmployeeSelfSensitiveChangeRequest;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeApprovalRecordVO;
import com.yiyundao.compensation.interfaces.vo.employee.EmployeeVO;
import com.yiyundao.compensation.modules.employee.dto.EmployeeProfileChangePayload;
import com.yiyundao.compensation.modules.employee.service.EmployeeService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/employee/me")
@RequiredArgsConstructor
@Tag(name = "员工自助资料", description = "员工本人维护资料（联系方式直改，敏感信息需审批）")
@SecurityRequirement(name = "Bearer")
public class EmployeeSelfController {

    private final EmployeeService employeeService;
    private final SysUserService sysUserService;

    @GetMapping
    @SecurityAnnotations.IsAuthenticated
    @Operation(summary = "获取本人资料", description = "返回当前登录用户绑定员工档案的详情信息")
    public ApiResponse<EmployeeVO> myProfile(Authentication authentication) {
        SysUser user = resolveCurrentUser(authentication);
        if (user == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return ApiResponse.success(employeeService.getCurrentEmployeeProfile(user.getId()));
    }

    @PatchMapping("/contact")
    @SecurityAnnotations.IsAuthenticated
    @Operation(summary = "更新本人联系方式", description = "手机号/邮箱直接修改并即时生效")
    public ApiResponse<EmployeeVO> updateMyContact(@RequestBody EmployeeSelfContactUpdateRequest request,
                                                   Authentication authentication) {
        SysUser user = resolveCurrentUser(authentication);
        if (user == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return ApiResponse.success(employeeService.updateCurrentEmployeeContact(
                user.getId(),
                request != null ? request.getPhone() : null,
                request != null ? request.getEmail() : null
        ));
    }

    @PostMapping("/change-requests")
    @SecurityAnnotations.IsAuthenticated
    @Operation(summary = "提交敏感信息变更申请", description = "姓名/身份证/收款账户等字段提交后进入审批流程")
    public ApiResponse<Map<String, Object>> submitChangeRequest(@RequestBody EmployeeSelfSensitiveChangeRequest request,
                                                                Authentication authentication) {
        SysUser user = resolveCurrentUser(authentication);
        if (user == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED, "未登录");
        }
        EmployeeProfileChangePayload payload = toPayload(request);
        Long workflowId = employeeService.submitCurrentEmployeeProfileChange(
                user.getId(),
                payload,
                request != null ? request.getReason() : null
        );
        return ApiResponse.success(Map.of(
                "workflowId", workflowId,
                "businessType", EmployeeService.BUSINESS_TYPE_EMPLOYEE_PROFILE_CHANGE,
                "status", "pending"
        ));
    }

    @GetMapping("/change-requests")
    @SecurityAnnotations.IsAuthenticated
    @Operation(summary = "查询我的敏感信息变更申请", description = "分页查询当前员工本人发起的敏感信息变更申请记录")
    public ApiResponse<PageResponse<EmployeeApprovalRecordVO>> myChangeRequests(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        SysUser user = resolveCurrentUser(authentication);
        if (user == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return ApiResponse.success(employeeService.pageCurrentEmployeeProfileChanges(user.getId(), page, size));
    }

    private SysUser resolveCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return sysUserService.findByUsername(authentication.getName());
    }

    private EmployeeProfileChangePayload toPayload(EmployeeSelfSensitiveChangeRequest request) {
        EmployeeProfileChangePayload payload = new EmployeeProfileChangePayload();
        if (request == null) {
            return payload;
        }
        payload.setName(request.getName());
        payload.setIdCard(request.getIdCard());
        payload.setSettlementAccountType(request.getSettlementAccountType());
        payload.setSettlementAccount(request.getSettlementAccount());
        payload.setSettlementAccountName(request.getSettlementAccountName());
        payload.setBankAccount(request.getBankAccount());
        payload.setBankName(request.getBankName());
        payload.setBankBranchName(request.getBankBranchName());
        return payload;
    }
}
