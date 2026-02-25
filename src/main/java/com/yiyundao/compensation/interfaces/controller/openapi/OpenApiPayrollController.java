package com.yiyundao.compensation.interfaces.controller.openapi;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.interfaces.dto.openapi.OpenApiPayrollBatchDto;
import com.yiyundao.compensation.interfaces.dto.openapi.OpenApiPayrollLineDto;
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

@RestController
@Validated
@RequestMapping("/v1/payroll")
@RequiredArgsConstructor
public class OpenApiPayrollController {

    private static final String PT_TYPE = "part_time";

    private final ExternalPayrollQueryService externalPayrollQueryService;
    private final ExternalApiContext externalApiContext;

    @GetMapping("/batches")
    @PreAuthorize("hasAuthority('SCOPE_payroll:read')")
    public ApiResponse<Page<OpenApiPayrollBatchDto>> pageBatches(@RequestParam(value = "type", required = false) String type,
                                                                 @RequestParam(value = "period", required = false) String period,
                                                                 @RequestParam(value = "status", required = false) String status,
                                                                 @RequestParam(value = "page", defaultValue = "1") long page,
                                                                 @RequestParam(value = "size", defaultValue = "20") long size) {
        ensureScope("payroll:read");
        if (StringUtils.hasText(type) && !PT_TYPE.equalsIgnoreCase(type.trim())) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID, "仅支持 type=part_time");
        }
        Page<OpenApiPayrollBatchDto> result = externalPayrollQueryService.pagePtBatches(period, status, page, size);
        return ApiResponse.success(result);
    }

    @GetMapping("/batches/{batchId}")
    @PreAuthorize("hasAuthority('SCOPE_payroll:read')")
    public ApiResponse<OpenApiPayrollBatchDto> getBatch(@PathVariable("batchId") Long batchId) {
        ensureScope("payroll:read");
        OpenApiPayrollBatchDto dto = externalPayrollQueryService.findBatch(batchId);
        if (dto == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "发薪批次不存在或不属于 PT 范围");
        }
        return ApiResponse.success(dto);
    }

    @GetMapping("/batches/{batchId}/lines")
    @PreAuthorize("hasAuthority('SCOPE_payroll:read')")
    public ApiResponse<Page<OpenApiPayrollLineDto>> pageBatchLines(@PathVariable("batchId") Long batchId,
                                                                   @RequestParam(value = "employeeRef", required = false) String employeeRef,
                                                                   @RequestParam(value = "page", defaultValue = "1") long page,
                                                                   @RequestParam(value = "size", defaultValue = "50") long size) {
        ensureScope("payroll:read");
        Page<OpenApiPayrollLineDto> result = externalPayrollQueryService.pageBatchLines(batchId, employeeRef, page, size);
        return ApiResponse.success(result);
    }

    private void ensureScope(String scope) {
        ExternalApiContext.ExternalApiClient client = externalApiContext.current();
        if (client == null || client.getScopes() == null || !client.getScopes().contains(scope)) {
            throw new org.springframework.security.access.AccessDeniedException("缺少访问范围" + scope);
        }
    }
}
