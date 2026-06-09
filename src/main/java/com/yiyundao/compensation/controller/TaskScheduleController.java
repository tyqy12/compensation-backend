package com.yiyundao.compensation.controller;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.common.response.ErrorCode;
import com.yiyundao.compensation.dto.TaskScheduleDTO;
import com.yiyundao.compensation.entity.ScheduledTask;
import com.yiyundao.compensation.entity.ScheduledTaskExecution;
import com.yiyundao.compensation.scheduler.TaskScheduler;
import com.yiyundao.compensation.security.SecurityAnnotations;
import com.yiyundao.compensation.service.ScheduledTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "任务调度管理")
@RestController
@RequestMapping("/v1/admin/tasks")
@SecurityAnnotations.IsAdmin
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
        ScheduledTask task = taskService.getById(id);
        if (task == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        return ApiResponse.success(task);
    }

    @Operation(summary = "创建任务")
    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody TaskScheduleDTO dto) {
        return ApiResponse.success(taskService.createTask(dto));
    }

    @Operation(summary = "更新任务")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody TaskScheduleDTO dto) {
        ScheduledTask task = taskService.getById(id);
        if (task == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        taskService.updateTask(id, dto);
        ScheduledTask updatedTask = taskService.getById(id);
        if (updatedTask != null && updatedTask.getStatus() == ScheduledTask.TaskStatus.RUNNING) {
            taskScheduler.resumeTask(updatedTask);
        } else {
            taskScheduler.pauseTask(id);
        }
        return ApiResponse.success();
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ScheduledTask task = taskService.getById(id);
        if (task == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        taskScheduler.pauseTask(id);
        taskService.deleteTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "暂停任务")
    @PostMapping("/{id}/pause")
    public ApiResponse<Void> pause(@PathVariable Long id) {
        ScheduledTask task = taskService.getById(id);
        if (task == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        taskScheduler.pauseTask(id);
        taskService.pauseTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "恢复任务")
    @PostMapping("/{id}/resume")
    public ApiResponse<Void> resume(@PathVariable Long id) {
        ScheduledTask task = taskService.getById(id);
        if (task == null) {
            return ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        taskService.resumeTask(id);
        ScheduledTask resumedTask = taskService.getById(id);
        taskScheduler.resumeTask(resumedTask);
        return ApiResponse.success();
    }

    @Operation(summary = "手动触发任务")
    @PostMapping("/{id}/trigger")
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
