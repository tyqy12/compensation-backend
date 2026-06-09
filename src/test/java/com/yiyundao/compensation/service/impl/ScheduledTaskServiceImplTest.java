package com.yiyundao.compensation.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yiyundao.compensation.dto.TaskScheduleDTO;
import com.yiyundao.compensation.entity.ScheduledTask;
import com.yiyundao.compensation.entity.ScheduledTaskExecution;
import com.yiyundao.compensation.mapper.ScheduledTaskExecutionMapper;
import com.yiyundao.compensation.service.TaskHandler;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledTaskServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                configuration,
                ScheduledTaskExecution.class.getName()
        );
        TableInfoHelper.initTableInfo(assistant, ScheduledTaskExecution.class);
    }

    @Test
    void getExecutionLogsShouldClampLimitBeforeAppendingSql() {
        ScheduledTaskExecutionMapper executionMapper = mock(ScheduledTaskExecutionMapper.class);
        ScheduledTaskServiceImpl service = new ScheduledTaskServiceImpl(
                executionMapper,
                mock(ApplicationContext.class)
        );
        when(executionMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<ScheduledTaskExecution>>any()))
                .thenReturn(List.of());

        service.getExecutionLogs(10L, -1);
        service.getExecutionLogs(10L, 1000);

        org.mockito.ArgumentCaptor<Wrapper<ScheduledTaskExecution>> wrapperCaptor =
                org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(executionMapper, org.mockito.Mockito.times(2)).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getAllValues().get(0).getSqlSegment()).contains("LIMIT 50");
        assertThat(wrapperCaptor.getAllValues().get(1).getSqlSegment()).contains("LIMIT 200");
    }

    @Test
    void createTaskShouldRejectSemanticallyInvalidCronExpression() {
        ScheduledTaskExecutionMapper executionMapper = mock(ScheduledTaskExecutionMapper.class);
        ScheduledTaskServiceImpl service = spy(new ScheduledTaskServiceImpl(
                executionMapper,
                mock(ApplicationContext.class)
        ));
        TaskScheduleDTO dto = taskDto();
        dto.setCronExpression("99 0 0 * * ?");

        assertThatThrownBy(() -> service.createTask(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无效的 Cron");

        verify(service, never()).save(org.mockito.ArgumentMatchers.any(ScheduledTask.class));
    }

    @Test
    void updateTaskShouldRejectSemanticallyInvalidCronExpressionWithoutPersisting() {
        ScheduledTaskExecutionMapper executionMapper = mock(ScheduledTaskExecutionMapper.class);
        ScheduledTaskServiceImpl service = spy(new ScheduledTaskServiceImpl(
                executionMapper,
                mock(ApplicationContext.class)
        ));
        ScheduledTask task = task();
        doReturn(task).when(service).getById(10L);
        TaskScheduleDTO dto = taskDto();
        dto.setCronExpression("0 61 0 * * ?");

        assertThatThrownBy(() -> service.updateTask(10L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无效的 Cron");

        verify(service, never()).updateById(task);
    }

    @Test
    void resumeTaskShouldRejectExistingInvalidCronExpression() {
        ScheduledTaskExecutionMapper executionMapper = mock(ScheduledTaskExecutionMapper.class);
        ScheduledTaskServiceImpl service = spy(new ScheduledTaskServiceImpl(
                executionMapper,
                mock(ApplicationContext.class)
        ));
        ScheduledTask task = task();
        task.setCronExpression("0 0 24 * * ?");
        doReturn(task).when(service).getById(10L);

        assertThatThrownBy(() -> service.resumeTask(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无效的 Cron");

        verify(service, never()).updateById(task);
    }

    @Test
    void triggerTaskShouldMarkExecutionSuccess() throws Exception {
        ScheduledTaskExecutionMapper executionMapper = mock(ScheduledTaskExecutionMapper.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        ScheduledTaskServiceImpl service = spy(new ScheduledTaskServiceImpl(executionMapper, applicationContext));
        ScheduledTask task = task();
        TaskHandler handler = mock(TaskHandler.class);
        doReturn(task).when(service).getById(10L);
        doReturn(true).when(service).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
        when(applicationContext.getBean("demoHandler", TaskHandler.class)).thenReturn(handler);
        when(handler.execute("demo-task", null)).thenReturn(TaskHandler.TaskExecutionResult.success("ok"));

        Long executionId = service.triggerTask(10L);

        org.mockito.ArgumentCaptor<ScheduledTaskExecution> executionCaptor =
                org.mockito.ArgumentCaptor.forClass(ScheduledTaskExecution.class);
        verify(executionMapper).updateById(executionCaptor.capture());
        assertThat(executionId).isNull();
        assertThat(executionCaptor.getValue().getStatus()).isEqualTo(ScheduledTaskExecution.ExecutionStatus.SUCCESS);
        assertThat(executionCaptor.getValue().getResult()).contains("SUCCESS: ok");
        assertThat(executionCaptor.getValue().getEndTime()).isNotNull();
        assertThat(executionCaptor.getValue().getDurationMs()).isNotNull();
    }

    @Test
    void triggerTaskShouldMarkExecutionFailedBeforeRethrowing() throws Exception {
        ScheduledTaskExecutionMapper executionMapper = mock(ScheduledTaskExecutionMapper.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        ScheduledTaskServiceImpl service = spy(new ScheduledTaskServiceImpl(executionMapper, applicationContext));
        ScheduledTask task = task();
        TaskHandler handler = mock(TaskHandler.class);
        doReturn(task).when(service).getById(10L);
        doReturn(true).when(service).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
        when(applicationContext.getBean("demoHandler", TaskHandler.class)).thenReturn(handler);
        when(handler.execute("demo-task", null)).thenReturn(TaskHandler.TaskExecutionResult.failure("boom"));

        assertThatThrownBy(() -> service.triggerTask(10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("任务执行失败");

        org.mockito.ArgumentCaptor<ScheduledTaskExecution> executionCaptor =
                org.mockito.ArgumentCaptor.forClass(ScheduledTaskExecution.class);
        verify(executionMapper).updateById(executionCaptor.capture());
        assertThat(executionCaptor.getValue().getStatus()).isEqualTo(ScheduledTaskExecution.ExecutionStatus.FAILED);
        assertThat(executionCaptor.getValue().getErrorMessage()).contains("任务执行失败");
        assertThat(executionCaptor.getValue().getEndTime()).isNotNull();
        assertThat(executionCaptor.getValue().getDurationMs()).isNotNull();
    }

    @Test
    void executeTaskShouldKeepRunningScheduleAfterSuccessfulRun() throws Exception {
        ScheduledTaskExecutionMapper executionMapper = mock(ScheduledTaskExecutionMapper.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        ScheduledTaskServiceImpl service = spy(new ScheduledTaskServiceImpl(executionMapper, applicationContext));
        ScheduledTask task = task();
        task.setStatus(ScheduledTask.TaskStatus.RUNNING);
        TaskHandler handler = mock(TaskHandler.class);
        doReturn(task).when(service).getById(10L);
        when(applicationContext.getBean("demoHandler", TaskHandler.class)).thenReturn(handler);
        when(handler.execute("demo-task", null)).thenReturn(TaskHandler.TaskExecutionResult.success("ok"));
        doReturn(true).when(service).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));

        service.executeTask(task);

        assertThat(task.getStatus()).isEqualTo(ScheduledTask.TaskStatus.RUNNING);
        assertThat(task.getLastResult()).isEqualTo("SUCCESS: ok");
        assertThat(task.getRetryCount()).isZero();
        assertThat(task.getNextExecuteTime()).isNotNull();
        verify(service).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
        verify(service, never()).updateById(task);
    }

    @Test
    void executeTaskShouldUseLatestTaskSnapshotInsteadOfScheduledStaleHandler() throws Exception {
        ScheduledTaskExecutionMapper executionMapper = mock(ScheduledTaskExecutionMapper.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        ScheduledTaskServiceImpl service = spy(new ScheduledTaskServiceImpl(executionMapper, applicationContext));
        ScheduledTask staleTask = task();
        staleTask.setStatus(ScheduledTask.TaskStatus.RUNNING);
        staleTask.setHandlerBean("oldHandler");
        ScheduledTask latestTask = task();
        latestTask.setStatus(ScheduledTask.TaskStatus.RUNNING);
        latestTask.setHandlerBean("newHandler");
        TaskHandler newHandler = mock(TaskHandler.class);
        doReturn(latestTask).when(service).getById(10L);
        when(applicationContext.getBean("newHandler", TaskHandler.class)).thenReturn(newHandler);
        when(newHandler.execute("demo-task", null)).thenReturn(TaskHandler.TaskExecutionResult.success("ok"));
        doReturn(true).when(service).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));

        service.executeTask(staleTask);

        verify(applicationContext, never()).getBean("oldHandler", TaskHandler.class);
        verify(newHandler).execute("demo-task", null);
        assertThat(staleTask.getLastResult()).isEqualTo("SUCCESS: ok");
        verify(service).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
    }

    @Test
    void executeTaskShouldKeepRunningScheduleUntilRetryLimitReached() throws Exception {
        ScheduledTaskExecutionMapper executionMapper = mock(ScheduledTaskExecutionMapper.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        ScheduledTaskServiceImpl service = spy(new ScheduledTaskServiceImpl(executionMapper, applicationContext));
        ScheduledTask task = task();
        task.setStatus(ScheduledTask.TaskStatus.RUNNING);
        task.setRetryCount(1);
        task.setMaxRetryCount(3);
        TaskHandler handler = mock(TaskHandler.class);
        doReturn(task).when(service).getById(10L);
        when(applicationContext.getBean("demoHandler", TaskHandler.class)).thenReturn(handler);
        when(handler.execute("demo-task", null)).thenReturn(TaskHandler.TaskExecutionResult.failure("boom"));
        doReturn(true).when(service).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));

        assertThatThrownBy(() -> service.executeTask(task))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("任务执行失败");

        assertThat(task.getStatus()).isEqualTo(ScheduledTask.TaskStatus.RUNNING);
        assertThat(task.getRetryCount()).isEqualTo(2);
        assertThat(task.getNextExecuteTime()).isNotNull();
        verify(service).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
        verify(service, never()).updateById(task);
    }

    @Test
    void executeTaskShouldFailScheduleWhenRetryLimitReached() throws Exception {
        ScheduledTaskExecutionMapper executionMapper = mock(ScheduledTaskExecutionMapper.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        ScheduledTaskServiceImpl service = spy(new ScheduledTaskServiceImpl(executionMapper, applicationContext));
        ScheduledTask task = task();
        task.setStatus(ScheduledTask.TaskStatus.RUNNING);
        task.setRetryCount(2);
        task.setMaxRetryCount(3);
        TaskHandler handler = mock(TaskHandler.class);
        doReturn(task).when(service).getById(10L);
        when(applicationContext.getBean("demoHandler", TaskHandler.class)).thenReturn(handler);
        when(handler.execute("demo-task", null)).thenReturn(TaskHandler.TaskExecutionResult.failure("boom"));
        doReturn(true).when(service).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));

        assertThatThrownBy(() -> service.executeTask(task))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("任务执行失败");

        assertThat(task.getStatus()).isEqualTo(ScheduledTask.TaskStatus.FAILED);
        assertThat(task.getRetryCount()).isEqualTo(3);
        assertThat(task.getNextExecuteTime()).isNull();
        verify(service).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
        verify(service, never()).updateById(task);
    }

    @Test
    void executeTaskShouldNotRewriteWholeTaskWhenOutcomeClaimLost() throws Exception {
        ScheduledTaskExecutionMapper executionMapper = mock(ScheduledTaskExecutionMapper.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        ScheduledTaskServiceImpl service = spy(new ScheduledTaskServiceImpl(executionMapper, applicationContext));
        ScheduledTask task = task();
        task.setStatus(ScheduledTask.TaskStatus.RUNNING);
        TaskHandler handler = mock(TaskHandler.class);
        doReturn(task).when(service).getById(10L);
        when(applicationContext.getBean("demoHandler", TaskHandler.class)).thenReturn(handler);
        when(handler.execute("demo-task", null)).thenReturn(TaskHandler.TaskExecutionResult.success("ok"));
        doReturn(false).when(service).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));

        service.executeTask(task);

        verify(service, times(1)).update(org.mockito.ArgumentMatchers.any(UpdateWrapper.class));
        verify(service, never()).updateById(task);
    }

    private ScheduledTask task() {
        ScheduledTask task = new ScheduledTask();
        task.setId(10L);
        task.setTaskKey("demo-task");
        task.setHandlerBean("demoHandler");
        task.setCronExpression("0 0/5 * * * ?");
        task.setRetryCount(0);
        task.setMaxRetryCount(3);
        task.setRetryIntervalSeconds(60);
        return task;
    }

    private TaskScheduleDTO taskDto() {
        TaskScheduleDTO dto = new TaskScheduleDTO();
        dto.setTaskKey("demo-task");
        dto.setTaskName("演示任务");
        dto.setTaskGroup("DEFAULT");
        dto.setCronExpression("0 0/5 * * * ?");
        dto.setHandlerBean("demoHandler");
        return dto;
    }
}
