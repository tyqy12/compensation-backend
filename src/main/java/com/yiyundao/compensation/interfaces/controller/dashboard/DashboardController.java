package com.yiyundao.compensation.interfaces.controller.dashboard;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.dto.dashboard.*;
import com.yiyundao.compensation.modules.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/metrics")
    public ApiResponse<DashboardMetricsDto> metrics() {
        return ApiResponse.success(dashboardService.collectMetrics());
    }

    @GetMapping("/status")
    public ApiResponse<SystemStatusDto> status() {
        return ApiResponse.success(dashboardService.collectStatus());
    }

    @GetMapping("/todos")
    public ApiResponse<List<TodoItemDto>> todos() {
        return ApiResponse.success(dashboardService.collectTodos());
    }

    @GetMapping("/activities")
    public ApiResponse<List<ActivityItemDto>> activities() {
        return ApiResponse.success(dashboardService.collectActivities());
    }
}
