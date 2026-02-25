package com.yiyundao.compensation.interfaces.controller.openapi;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.openapi.OpenApiPayslipDto;
import com.yiyundao.compensation.modules.payroll.service.ExternalPayrollQueryService;
import com.yiyundao.compensation.security.ExternalApiContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.YearMonth;
import java.util.List;

@RestController
@Validated
@RequestMapping("/v1/payslips")
@RequiredArgsConstructor
public class OpenApiPayslipController {

    private final ExternalPayrollQueryService externalPayrollQueryService;
    private final ExternalApiContext externalApiContext;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_payslip:read')")
    public ApiResponse<List<OpenApiPayslipDto>> queryPayslips(@RequestParam("employeeRef") String employeeRef,
                                                              @RequestParam("period") String period) {
        ensureScope("payslip:read");
        if (!StringUtils.hasText(employeeRef) || !StringUtils.hasText(period)) {
            return ApiResponse.error(ErrorCode.PARAM_MISSING, "employeeRef 与 period 不能为空");
        }
        if (!isYearMonth(period.trim())) {
            return ApiResponse.error(ErrorCode.PARAM_FORMAT_ERROR, "period 必须符合 YYYY-MM 格式");
        }
        List<OpenApiPayslipDto> payload = externalPayrollQueryService.findPayslips(employeeRef.trim(), period.trim());
        return ApiResponse.success(payload);
    }

    @GetMapping("/{payslipId}")
    @PreAuthorize("hasAuthority('SCOPE_payslip:read')")
    public ApiResponse<OpenApiPayslipDto> getPayslip(@PathVariable("payslipId") Long payslipId) {
        ensureScope("payslip:read");
        OpenApiPayslipDto dto = externalPayrollQueryService.findPayslip(payslipId);
        if (dto == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "工资条不存在或不属于 PT 范围");
        }
        return ApiResponse.success(dto);
    }

    private void ensureScope(String scope) {
        ExternalApiContext.ExternalApiClient client = externalApiContext.current();
        if (client == null || client.getScopes() == null || !client.getScopes().contains(scope)) {
            throw new org.springframework.security.access.AccessDeniedException("缺少访问范围" + scope);
        }
    }

    private boolean isYearMonth(String value) {
        try {
            YearMonth.parse(value, DateTimeFormatter.ofPattern("yyyy-MM"));
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
