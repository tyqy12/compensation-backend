package com.yiyundao.compensation.interfaces.controller.payroll;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.interfaces.dto.payroll.PayrollBasicReportDto;
import com.yiyundao.compensation.modules.payroll.service.PayrollReportService;
import com.yiyundao.compensation.security.SecurityAnnotations;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payroll/reports")
@RequiredArgsConstructor
public class PayrollReportController {

    private final PayrollReportService payrollReportService;

    @GetMapping("/basic")
    @SecurityAnnotations.IsFinanceOrAdmin
    public ApiResponse<PayrollBasicReportDto> basic(@RequestParam(required = false) Long batchId,
                                                   @RequestParam(required = false) String periodLabel,
                                                   @RequestParam(required = false) String department) {
        return ApiResponse.success(payrollReportService.basicReport(batchId, periodLabel, department));
    }

    @GetMapping("/basic/export")
    @SecurityAnnotations.IsFinanceOrAdmin
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long batchId,
                                         @RequestParam(required = false) String periodLabel,
                                         @RequestParam(required = false) String department) {
        byte[] payload = payrollReportService.exportBasicReport(batchId, periodLabel, department);
        String filename = "payroll-basic-report.csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(payload);
    }
}

