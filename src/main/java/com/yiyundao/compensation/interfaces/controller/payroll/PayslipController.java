package com.yiyundao.compensation.interfaces.controller.payroll;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.payroll.EmployeePayslipDto;
import com.yiyundao.compensation.modules.payroll.service.PayslipService;
import com.yiyundao.compensation.modules.user.entity.SysUser;
import com.yiyundao.compensation.modules.user.service.SysUserService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payroll/payslips")
@RequiredArgsConstructor
public class PayslipController {

    private final PayslipService payslipService;
    private final SysUserService sysUserService;

    @GetMapping
    @SecurityAnnotations.IsEmployeeOrFinanceOrAdmin
    public ApiResponse<Page<EmployeePayslipDto.PayslipSummary>> list(@RequestParam(defaultValue = "1") int page,
                                                                     @RequestParam(defaultValue = "10") int size,
                                                                     @RequestParam(required = false) Long employeeId) {
        SysUser current = currentUser();
        Page<EmployeePayslipDto.PayslipSummary> result = payslipService.pagePayslips(current, employeeId, page, size);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    @SecurityAnnotations.IsEmployeeOrFinanceOrAdmin
    public ApiResponse<EmployeePayslipDto.PayslipDetail> detail(@PathVariable Long id) {
        SysUser current = currentUser();
        EmployeePayslipDto.PayslipDetail detail = payslipService.getPayslipDetail(current, id);
        if (detail == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "工资条不存在");
        }
        return ApiResponse.success(detail);
    }

    @GetMapping("/{id}/download")
    @SecurityAnnotations.IsEmployeeOrFinanceOrAdmin
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        SysUser current = currentUser();
        byte[] payload = payslipService.exportPayslip(current, id);
        if (payload == null) {
            return ResponseEntity.notFound().build();
        }
        String filename = "payslip-" + id + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(payload);
    }

    private SysUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        String username = authentication.getName();
        return username == null ? null : sysUserService.findByUsername(username);
    }
}
