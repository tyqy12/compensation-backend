package com.yiyundao.compensation.controller;

import com.yiyundao.compensation.common.response.ApiResponse;
import com.yiyundao.compensation.dto.TaskScheduleDTO;
import com.yiyundao.compensation.entity.ScheduledTask;
import com.yiyundao.compensation.scheduler.TaskScheduler;
import com.yiyundao.compensation.service.ScheduledTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

class TaskScheduleControllerTest {

    private final ScheduledTaskService taskService = mock(ScheduledTaskService.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final TaskScheduleController controller = new TaskScheduleController(taskService, taskScheduler);

    @Test
    void getShouldReturnNotFoundWhenTaskMissing() {
        when(taskService.getById(10L)).thenReturn(null);

        ApiResponse<ScheduledTask> response = controller.get(10L);

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("任务不存在");
    }

    @Test
    void resumeShouldReturnNotFoundWhenTaskMissing() {
        when(taskService.getById(11L)).thenReturn(null);

        ApiResponse<Void> response = controller.resume(11L);

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("任务不存在");
        verify(taskScheduler, never()).resumeTask(org.mockito.ArgumentMatchers.any());
        verify(taskService, never()).resumeTask(11L);
    }

    @Test
    void resumeShouldPersistRunningStatusBeforeSchedulingLatestTask() {
        ScheduledTask existing = new ScheduledTask();
        existing.setId(11L);
        existing.setStatus(ScheduledTask.TaskStatus.PAUSED);
        ScheduledTask resumed = new ScheduledTask();
        resumed.setId(11L);
        resumed.setStatus(ScheduledTask.TaskStatus.RUNNING);
        when(taskService.getById(11L)).thenReturn(existing, resumed);

        ApiResponse<Void> response = controller.resume(11L);

        assertThat(response.getCode()).isZero();
        InOrder inOrder = inOrder(taskService, taskScheduler);
        inOrder.verify(taskService).getById(11L);
        inOrder.verify(taskService).resumeTask(11L);
        inOrder.verify(taskService).getById(11L);
        inOrder.verify(taskScheduler).resumeTask(resumed);
    }

    @Test
    void updateShouldRescheduleRunningTaskAfterPersistingChanges() {
        ScheduledTask existing = new ScheduledTask();
        existing.setId(14L);
        existing.setStatus(ScheduledTask.TaskStatus.RUNNING);
        ScheduledTask updated = new ScheduledTask();
        updated.setId(14L);
        updated.setStatus(ScheduledTask.TaskStatus.RUNNING);
        TaskScheduleDTO dto = new TaskScheduleDTO();
        when(taskService.getById(14L)).thenReturn(existing, updated);

        ApiResponse<Void> response = controller.update(14L, dto);

        assertThat(response.getCode()).isZero();
        InOrder inOrder = inOrder(taskService, taskScheduler);
        inOrder.verify(taskService).getById(14L);
        inOrder.verify(taskService).updateTask(14L, dto);
        inOrder.verify(taskService).getById(14L);
        inOrder.verify(taskScheduler).resumeTask(updated);
        verify(taskScheduler, never()).pauseTask(14L);
    }

    @Test
    void updateShouldPauseInMemoryScheduleWhenTaskIsNotRunningAfterUpdate() {
        ScheduledTask existing = new ScheduledTask();
        existing.setId(15L);
        existing.setStatus(ScheduledTask.TaskStatus.RUNNING);
        ScheduledTask updated = new ScheduledTask();
        updated.setId(15L);
        updated.setStatus(ScheduledTask.TaskStatus.PAUSED);
        TaskScheduleDTO dto = new TaskScheduleDTO();
        when(taskService.getById(15L)).thenReturn(existing, updated);

        ApiResponse<Void> response = controller.update(15L, dto);

        assertThat(response.getCode()).isZero();
        InOrder inOrder = inOrder(taskService, taskScheduler);
        inOrder.verify(taskService).getById(15L);
        inOrder.verify(taskService).updateTask(15L, dto);
        inOrder.verify(taskService).getById(15L);
        inOrder.verify(taskScheduler).pauseTask(15L);
        verify(taskScheduler, never()).resumeTask(updated);
    }

    @Test
    void updateShouldReturnNotFoundWhenTaskMissing() {
        TaskScheduleDTO dto = new TaskScheduleDTO();
        when(taskService.getById(16L)).thenReturn(null);

        ApiResponse<Void> response = controller.update(16L, dto);

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("任务不存在");
        verify(taskService, never()).updateTask(16L, dto);
        verify(taskScheduler, never()).pauseTask(16L);
        verify(taskScheduler, never()).resumeTask(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void pauseShouldReturnNotFoundWhenTaskMissing() {
        when(taskService.getById(12L)).thenReturn(null);

        ApiResponse<Void> response = controller.pause(12L);

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("任务不存在");
        verify(taskScheduler, never()).pauseTask(12L);
        verify(taskService, never()).pauseTask(12L);
    }

    @Test
    void deleteShouldReturnNotFoundWhenTaskMissing() {
        when(taskService.getById(13L)).thenReturn(null);

        ApiResponse<Void> response = controller.delete(13L);

        assertThat(response.getCode()).isEqualTo(1002);
        assertThat(response.getMessage()).contains("任务不存在");
        verify(taskScheduler, never()).pauseTask(13L);
        verify(taskService, never()).deleteTask(13L);
    }
}
