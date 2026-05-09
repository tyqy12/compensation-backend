package com.yiyundao.compensation.modules.payment.controller;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.enums.EmploymentType;
import com.yiyundao.compensation.modules.payment.dto.EmployeeTypeMappingDto;
import com.yiyundao.compensation.modules.payment.entity.EmployeeTypeProviderMapping;
import com.yiyundao.compensation.modules.payment.service.SettlementProviderRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 结算渠道路由配置管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/settlement/routing")
@RequiredArgsConstructor
public class SettlementProviderRoutingController {

    private final SettlementProviderRoutingService routingService;

    /**
     * 创建员工类型映射
     */
    @PostMapping("/mapping")
    public ApiResponse<EmployeeTypeProviderMapping> createMapping(@Valid @RequestBody EmployeeTypeMappingDto dto) {
        EmployeeTypeProviderMapping mapping = routingService.createMapping(
            dto.getEmploymentType(),
            dto.getProviderCode(),
            dto.getPriority()
        );
        return ApiResponse.success(mapping);
    }

    /**
     * 更新员工类型映射
     */
    @PutMapping("/mapping/{id}")
    public ApiResponse<EmployeeTypeProviderMapping> updateMapping(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeTypeMappingDto dto) {
        EmployeeTypeProviderMapping mapping = routingService.updateMapping(
            id,
            dto.getProviderCode(),
            dto.getPriority()
        );
        return ApiResponse.success(mapping);
    }

    /**
     * 删除员工类型映射
     */
    @DeleteMapping("/mapping/{id}")
    public ApiResponse<Void> deleteMapping(@PathVariable Long id) {
        routingService.deleteMapping(id);
        return ApiResponse.success(null);
    }

    /**
     * 获取指定员工类型的所有映射
     */
    @GetMapping("/mapping/type/{employmentType}")
    public ApiResponse<List<EmployeeTypeProviderMapping>> getMappingsByType(
            @PathVariable EmploymentType employmentType) {
        List<EmployeeTypeProviderMapping> mappings = routingService.getMappingsByEmploymentType(employmentType);
        return ApiResponse.success(mappings);
    }

    /**
     * 获取所有映射
     */
    @GetMapping("/mapping")
    public ApiResponse<List<EmployeeTypeProviderMapping>> getAllMappings() {
        List<EmployeeTypeProviderMapping> mappings = routingService.getAllMappings();
        return ApiResponse.success(mappings);
    }

    /**
     * 启用/禁用映射
     */
    @PatchMapping("/mapping/{id}/status")
    public ApiResponse<Void> toggleMappingStatus(
            @PathVariable Long id,
            @RequestParam Boolean enabled) {
        routingService.toggleMappingStatus(id, enabled);
        return ApiResponse.success(null);
    }
}
