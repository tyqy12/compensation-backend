package com.yiyundao.compensation.controller;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.dto.TaskScheduleDTO;
import com.yiyundao.compensation.entity.ScheduledTask;
import com.yiyundao.compensation.entity.ScheduledTaskExecution;
import com.yiyundao.compensation.scheduler.TaskScheduler;
import com.yiyundao.compensation.service.ScheduledTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "任务调度管理")
@RestController
@RequestMapping("/v1/admin/tasks")
@RequiredArgsConstructor
public class TaskScheduleController {

    private final ScheduledTaskService taskService;
    private final TaskScheduler taskScheduler;

    @Operation(summary = "获取任务列表")
    @GetMapping
    public ApiResponse<List<ScheduledTask>> list() {
        return ApiResponse.success(taskService.list());
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{id}")
    public ApiResponse<ScheduledTask> get(@PathVariable Long id) {
        return ApiResponse.success(taskService.getById(id));
    }

    @Operation(summary = "创建任务")
    @PostMapping
    @PreAuthorize("hasAuthority('task:create')")
    public ApiResponse<Long> create(@Valid @RequestBody TaskScheduleDTO dto) {
        return ApiResponse.success(taskService.createTask(dto));
    }

    @Operation(summary = "更新任务")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('task:update')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody TaskScheduleDTO dto) {
        taskService.updateTask(id, dto);
        return ApiResponse.success();
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('task:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        taskScheduler.pauseTask(id);
        taskService.deleteTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "暂停任务")
    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('task:pause')")
    public ApiResponse<Void> pause(@PathVariable Long id) {
        taskScheduler.pauseTask(id);
        taskService.pauseTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "恢复任务")
    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('task:resume')")
    public ApiResponse<Void> resume(@PathVariable Long id) {
        ScheduledTask task = taskService.getById(id);
        if (task != null) {
            taskScheduler.resumeTask(task);
            taskService.resumeTask(id);
        }
        return ApiResponse.success();
    }

    @Operation(summary = "手动触发任务")
    @PostMapping("/{id}/trigger")
    @PreAuthorize("hasAuthority('task:trigger')")
    public ApiResponse<Long> trigger(@PathVariable Long id) {
        return ApiResponse.success(taskService.triggerTask(id));
    }

    @Operation(summary = "获取执行日志")
    @GetMapping("/{id}/logs")
    public ApiResponse<List<ScheduledTaskExecution>> getLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(taskService.getExecutionLogs(id, limit));
    }
}
