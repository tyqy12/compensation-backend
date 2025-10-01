package com.yiyundao.compensation.interfaces.controller.payroll;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.modules.payroll.service.PayrollImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/payroll/import")
@RequiredArgsConstructor
public class PayrollImportController {

    private final PayrollImportService payrollImportService;

    // 预览导入（CSV），不落库
    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ApiResponse<String> preview(@RequestParam Long batchId, @RequestParam("file") MultipartFile file) {
        // TODO: RBAC finance-only; CSV header expected: employeeId,itemCode,amount,note
        return ApiResponse.success(payrollImportService.previewCsv(batchId, file));
    }

    // 提交导入（CSV），写入暂存表
    @PostMapping("/commit")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ApiResponse<String> commit(@RequestParam Long batchId, @RequestParam("file") MultipartFile file) {
        // TODO: after commit, trigger optional compute preview or notify manager for check
        return ApiResponse.success(payrollImportService.commitCsv(batchId, file));
    }
}
