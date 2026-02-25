package com.yiyundao.compensation.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("定时任务实体测试")
class ScheduledTaskTest {

    @Test
    @DisplayName("任务状态枚举测试")
    void testTaskStatus() {
        assertEquals(0, ScheduledTask.TaskStatus.PAUSED.ordinal());
        assertEquals(1, ScheduledTask.TaskStatus.RUNNING.ordinal());
        assertEquals(2, ScheduledTask.TaskStatus.FAILED.ordinal());
        assertEquals(3, ScheduledTask.TaskStatus.SUCCESS.ordinal());
    }

    @Test
    @DisplayName("任务实体创建测试")
    void testScheduledTaskCreation() {
        ScheduledTask task = new ScheduledTask();
        task.setId(1L);
        task.setTaskKey("daily_salary_sync");
        task.setTaskName("每日薪资同步");
        task.setTaskGroup("SALARY");
        task.setCronExpression("0 0 2 * * ?");
        task.setDescription("每天凌晨2点同步薪资数据");
        task.setStatus(ScheduledTask.TaskStatus.RUNNING);
        task.setMaxRetryCount(3);
        task.setRetryIntervalSeconds(60);
        task.setAlarmEnabled(true);
        task.setAlarmReceivers("admin@example.com");

        assertEquals(1L, task.getId());
        assertEquals("daily_salary_sync", task.getTaskKey());
        assertEquals("每日薪资同步", task.getTaskName());
        assertEquals("SALARY", task.getTaskGroup());
        assertEquals("0 0 2 * * ?", task.getCronExpression());
        assertEquals(ScheduledTask.TaskStatus.RUNNING, task.getStatus());
        assertEquals(3, task.getMaxRetryCount());
        assertTrue(task.getAlarmEnabled());
    }

    @Test
    @DisplayName("任务实体默认状态测试")
    void testScheduledTaskDefaultStatus() {
        ScheduledTask task = new ScheduledTask();

        assertNull(task.getId());
        assertNull(task.getStatus());
        assertNull(task.getRetryCount());
        assertNull(task.getLastExecuteTime());
        assertNull(task.getNextExecuteTime());
    }

    @Test
    @DisplayName("任务执行记录枚举测试")
    void testExecutionStatus() {
        assertEquals(0, ScheduledTaskExecution.ExecutionStatus.RUNNING.ordinal());
        assertEquals(1, ScheduledTaskExecution.ExecutionStatus.SUCCESS.ordinal());
        assertEquals(2, ScheduledTaskExecution.ExecutionStatus.FAILED.ordinal());
        assertEquals(3, ScheduledTaskExecution.ExecutionStatus.RETRYING.ordinal());
    }

    @Test
    @DisplayName("任务执行记录创建测试")
    void testScheduledTaskExecutionCreation() {
        ScheduledTaskExecution execution = new ScheduledTaskExecution();
        execution.setId(1L);
        execution.setTaskId(100L);
        execution.setTaskKey("daily_salary_sync");
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus(ScheduledTaskExecution.ExecutionStatus.RUNNING);
        execution.setTraceId("abc123def456");

        assertEquals(1L, execution.getId());
        assertEquals(100L, execution.getTaskId());
        assertEquals("daily_salary_sync", execution.getTaskKey());
        assertEquals(ScheduledTaskExecution.ExecutionStatus.RUNNING, execution.getStatus());
        assertEquals("abc123def456", execution.getTraceId());
    }

    @Test
    @DisplayName("任务执行记录完成测试")
    void testExecutionCompletion() {
        ScheduledTaskExecution execution = new ScheduledTaskExecution();
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus(ScheduledTaskExecution.ExecutionStatus.RUNNING);

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        execution.setEndTime(LocalDateTime.now());
        execution.setDurationMs(100L);
        execution.setStatus(ScheduledTaskExecution.ExecutionStatus.SUCCESS);
        execution.setResult("执行成功");

        assertNotNull(execution.getEndTime());
        assertEquals(100L, execution.getDurationMs());
        assertEquals(ScheduledTaskExecution.ExecutionStatus.SUCCESS, execution.getStatus());
        assertEquals("执行成功", execution.getResult());
    }

    @Test
    @DisplayName("任务执行记录失败测试")
    void testExecutionFailure() {
        ScheduledTaskExecution execution = new ScheduledTaskExecution();
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus(ScheduledTaskExecution.ExecutionStatus.RUNNING);

        execution.setEndTime(LocalDateTime.now());
        execution.setStatus(ScheduledTaskExecution.ExecutionStatus.FAILED);
        execution.setErrorMessage("数据库连接失败");

        assertEquals(ScheduledTaskExecution.ExecutionStatus.FAILED, execution.getStatus());
        assertEquals("数据库连接失败", execution.getErrorMessage());
    }
}
