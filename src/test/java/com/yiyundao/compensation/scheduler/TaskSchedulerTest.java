package com.yiyundao.compensation.scheduler;

import com.yiyundao.compensation.entity.ScheduledTask;
import com.yiyundao.compensation.entity.ScheduledTaskExecution;
import com.yiyundao.compensation.mapper.ScheduledTaskExecutionMapper;
import com.yiyundao.compensation.service.ScheduledTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskSchedulerTest {

    @Test
    void scheduleTaskShouldStopWhenLatestTaskIsNotRunning() {
        ScheduledTaskService taskService = mock(ScheduledTaskService.class);
        ThreadPoolTaskScheduler threadPoolTaskScheduler = mock(ThreadPoolTaskScheduler.class);
        TaskScheduler scheduler = new TaskScheduler(
                taskService,
                threadPoolTaskScheduler,
                mock(ScheduledTaskExecutionMapper.class)
        );
        ScheduledTask task = task(ScheduledTask.TaskStatus.RUNNING);
        ScheduledTask latest = task(ScheduledTask.TaskStatus.FAILED);
        when(threadPoolTaskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));
        when(taskService.getById(10L)).thenReturn(latest);

        scheduler.scheduleTask(task);

        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(threadPoolTaskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
        assertThat(triggerCaptor.getValue().nextExecution(mock(TriggerContext.class))).isNull();
    }

    @Test
    void scheduleTaskShouldCancelExistingFutureBeforeReplacingIt() {
        ScheduledTaskService taskService = mock(ScheduledTaskService.class);
        ThreadPoolTaskScheduler threadPoolTaskScheduler = mock(ThreadPoolTaskScheduler.class);
        ScheduledFuture<?> firstFuture = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondFuture = mock(ScheduledFuture.class);
        TaskScheduler scheduler = new TaskScheduler(
                taskService,
                threadPoolTaskScheduler,
                mock(ScheduledTaskExecutionMapper.class)
        );
        doReturn(firstFuture, secondFuture)
                .when(threadPoolTaskScheduler)
                .schedule(any(Runnable.class), any(Trigger.class));
        ScheduledTask task = task(ScheduledTask.TaskStatus.RUNNING);

        scheduler.scheduleTask(task);
        scheduler.scheduleTask(task);

        verify(firstFuture).cancel(false);
    }

    @Test
    void scheduleTaskShouldUsePersistedNextExecuteTimeForRetry() {
        ScheduledTaskService taskService = mock(ScheduledTaskService.class);
        ThreadPoolTaskScheduler threadPoolTaskScheduler = mock(ThreadPoolTaskScheduler.class);
        TaskScheduler scheduler = new TaskScheduler(
                taskService,
                threadPoolTaskScheduler,
                mock(ScheduledTaskExecutionMapper.class)
        );
        ScheduledTask task = task(ScheduledTask.TaskStatus.RUNNING);
        LocalDateTime retryAt = LocalDateTime.now().plusSeconds(30);
        ScheduledTask latest = task(ScheduledTask.TaskStatus.RUNNING);
        latest.setNextExecuteTime(retryAt);
        when(threadPoolTaskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));
        when(taskService.getById(10L)).thenReturn(latest);

        scheduler.scheduleTask(task);

        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(threadPoolTaskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
        assertThat(triggerCaptor.getValue().nextExecution(mock(TriggerContext.class)))
                .isEqualTo(retryAt.atZone(java.time.ZoneId.systemDefault()).toInstant());
    }

    @Test
    void scheduleTaskShouldStopWhenCronCannotCalculateNextExecutionTime() {
        ScheduledTaskService taskService = mock(ScheduledTaskService.class);
        ThreadPoolTaskScheduler threadPoolTaskScheduler = mock(ThreadPoolTaskScheduler.class);
        TaskScheduler scheduler = new TaskScheduler(
                taskService,
                threadPoolTaskScheduler,
                mock(ScheduledTaskExecutionMapper.class)
        );
        ScheduledTask task = task(ScheduledTask.TaskStatus.RUNNING);
        ScheduledTask latest = task(ScheduledTask.TaskStatus.RUNNING);
        latest.setCronExpression("99 0 0 * * ?");
        latest.setNextExecuteTime(null);
        when(threadPoolTaskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));
        when(taskService.getById(10L)).thenReturn(latest);
        when(taskService.calculateNextExecutionTime("99 0 0 * * ?")).thenReturn(null);

        scheduler.scheduleTask(task);

        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(threadPoolTaskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
        assertThat(triggerCaptor.getValue().nextExecution(mock(TriggerContext.class))).isNull();
    }

    @Test
    void scheduledRunShouldRecordFailureWhenAlarmEnabledIsNull() {
        ScheduledTaskService taskService = mock(ScheduledTaskService.class);
        ThreadPoolTaskScheduler threadPoolTaskScheduler = mock(ThreadPoolTaskScheduler.class);
        ScheduledTaskExecutionMapper executionMapper = mock(ScheduledTaskExecutionMapper.class);
        TaskScheduler scheduler = new TaskScheduler(
                taskService,
                threadPoolTaskScheduler,
                executionMapper
        );
        ScheduledTask task = task(ScheduledTask.TaskStatus.RUNNING);
        task.setAlarmEnabled(null);
        when(threadPoolTaskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));
        when(taskService.getById(10L)).thenReturn(task);
        doThrow(new RuntimeException("boom")).when(taskService).executeTask(task);

        scheduler.scheduleTask(task);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(threadPoolTaskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
        assertThatCode(() -> runnableCaptor.getValue().run()).doesNotThrowAnyException();

        ArgumentCaptor<ScheduledTaskExecution> executionCaptor =
                ArgumentCaptor.forClass(ScheduledTaskExecution.class);
        verify(executionMapper).updateById(executionCaptor.capture());
        assertThat(executionCaptor.getValue().getStatus()).isEqualTo(ScheduledTaskExecution.ExecutionStatus.FAILED);
        assertThat(executionCaptor.getValue().getErrorMessage()).contains("boom");
    }

    @Test
    void scheduledRunShouldUseLatestTaskSnapshot() {
        ScheduledTaskService taskService = mock(ScheduledTaskService.class);
        ThreadPoolTaskScheduler threadPoolTaskScheduler = mock(ThreadPoolTaskScheduler.class);
        ScheduledTaskExecutionMapper executionMapper = mock(ScheduledTaskExecutionMapper.class);
        TaskScheduler scheduler = new TaskScheduler(
                taskService,
                threadPoolTaskScheduler,
                executionMapper
        );
        ScheduledTask task = task(ScheduledTask.TaskStatus.RUNNING);
        task.setTaskKey("stale-task");
        ScheduledTask latest = task(ScheduledTask.TaskStatus.RUNNING);
        latest.setTaskKey("latest-task");
        when(threadPoolTaskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));
        when(taskService.getById(10L)).thenReturn(latest);

        scheduler.scheduleTask(task);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(threadPoolTaskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
        runnableCaptor.getValue().run();

        verify(taskService).executeTask(latest);
        verify(taskService, never()).executeTask(task);
        ArgumentCaptor<ScheduledTaskExecution> executionCaptor =
                ArgumentCaptor.forClass(ScheduledTaskExecution.class);
        verify(executionMapper).insert(executionCaptor.capture());
        assertThat(executionCaptor.getValue().getTaskKey()).isEqualTo("latest-task");
    }

    @Test
    void scheduledRunShouldSkipWhenTaskWasPausedBeforeRunnableStarts() {
        ScheduledTaskService taskService = mock(ScheduledTaskService.class);
        ThreadPoolTaskScheduler threadPoolTaskScheduler = mock(ThreadPoolTaskScheduler.class);
        ScheduledTaskExecutionMapper executionMapper = mock(ScheduledTaskExecutionMapper.class);
        TaskScheduler scheduler = new TaskScheduler(
                taskService,
                threadPoolTaskScheduler,
                executionMapper
        );
        ScheduledTask task = task(ScheduledTask.TaskStatus.RUNNING);
        ScheduledTask paused = task(ScheduledTask.TaskStatus.PAUSED);
        when(threadPoolTaskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));
        when(taskService.getById(10L)).thenReturn(paused);

        scheduler.scheduleTask(task);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(threadPoolTaskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
        runnableCaptor.getValue().run();

        verify(taskService, never()).executeTask(any(ScheduledTask.class));
        verify(executionMapper, never()).insert(any(ScheduledTaskExecution.class));
        verify(executionMapper, never()).updateById(any(ScheduledTaskExecution.class));
    }

    private ScheduledTask task(ScheduledTask.TaskStatus status) {
        ScheduledTask task = new ScheduledTask();
        task.setId(10L);
        task.setTaskKey("demo-task");
        task.setCronExpression("0 0/5 * * * ?");
        task.setStatus(status);
        return task;
    }
}
