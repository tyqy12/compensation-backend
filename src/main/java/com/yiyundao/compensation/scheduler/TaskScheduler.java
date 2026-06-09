package com.yiyundao.compensation.scheduler;

import com.yiyundao.compensation.entity.ScheduledTask;
import com.yiyundao.compensation.entity.ScheduledTaskExecution;
import com.yiyundao.compensation.mapper.ScheduledTaskExecutionMapper;
import com.yiyundao.compensation.service.ScheduledTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskScheduler {

    private final ScheduledTaskService taskService;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final ScheduledTaskExecutionMapper executionMapper;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            // 加载所有启用的任务
            taskService.listTasksByStatus(ScheduledTask.TaskStatus.RUNNING)
                    .forEach(this::scheduleTask);
            log.info("调度任务初始化完成，已加载 {} 个任务", scheduledTasks.size());
        } catch (Exception e) {
            // 如果表不存在或其他错误，记录警告但不阻止应用启动
            log.warn("调度任务初始化失败（可能是数据库表未创建），将在表创建后自动加载: {}", e.getMessage());
        }
    }

    public void scheduleTask(ScheduledTask task) {
        try {
            if (task == null || task.getId() == null) {
                return;
            }
            pauseTask(task.getId());
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> executeTask(task),
                    triggerContext -> {
                        ScheduledTask latestTask = taskService.getById(task.getId());
                        if (latestTask == null || latestTask.getStatus() != ScheduledTask.TaskStatus.RUNNING) {
                            scheduledTasks.remove(task.getId());
                            return null;
                        }
                        LocalDateTime nextTime = latestTask.getNextExecuteTime();
                        if (nextTime == null) {
                            nextTime = taskService.calculateNextExecutionTime(latestTask.getCronExpression());
                        }
                        if (nextTime == null) {
                            scheduledTasks.remove(task.getId());
                            log.warn("任务 Cron 表达式无效，停止调度: taskId={}, taskKey={}, cron={}",
                                    latestTask.getId(), latestTask.getTaskKey(), latestTask.getCronExpression());
                            return null;
                        }
                        return nextTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
                    }
            );

            scheduledTasks.put(task.getId(), future);
            log.info("任务已调度: taskKey={}", task.getTaskKey());

        } catch (Exception e) {
            log.error("调度任务失败: taskKey={}", task.getTaskKey(), e);
        }
    }

    private void executeTask(ScheduledTask task) {
        ScheduledTask latestTask = taskService.getById(task.getId());
        if (latestTask == null || latestTask.getStatus() != ScheduledTask.TaskStatus.RUNNING) {
            scheduledTasks.remove(task.getId());
            log.info("任务已停止调度，跳过本次执行: taskId={}, taskKey={}", task.getId(), task.getTaskKey());
            return;
        }

        ScheduledTask executionTask = latestTask;
        log.info("开始执行任务: taskKey={}", executionTask.getTaskKey());
        long startTime = System.currentTimeMillis();

        ScheduledTaskExecution execution = new ScheduledTaskExecution();
        execution.setTaskId(executionTask.getId());
        execution.setTaskKey(executionTask.getTaskKey());
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus(ScheduledTaskExecution.ExecutionStatus.RUNNING);
        executionMapper.insert(execution);

        try {
            taskService.executeTask(executionTask);

            execution.setEndTime(LocalDateTime.now());
            execution.setDurationMs(System.currentTimeMillis() - startTime);
            execution.setStatus(ScheduledTaskExecution.ExecutionStatus.SUCCESS);
            execution.setResult("执行成功");
            executionMapper.updateById(execution);

            log.info("任务执行成功: taskKey={}", executionTask.getTaskKey());

        } catch (Exception e) {
            log.error("任务执行失败: taskKey={}", executionTask.getTaskKey(), e);

            execution.setEndTime(LocalDateTime.now());
            execution.setDurationMs(System.currentTimeMillis() - startTime);
            execution.setStatus(ScheduledTaskExecution.ExecutionStatus.FAILED);
            execution.setErrorMessage(e.getMessage());
            executionMapper.updateById(execution);

            // 发送告警
            if (Boolean.TRUE.equals(executionTask.getAlarmEnabled())) {
                sendAlarm(executionTask, e.getMessage());
            }
        }
    }

    private void sendAlarm(ScheduledTask task, String error) {
        log.warn("任务执行失败告警: taskKey={}, error={}", task.getTaskKey(), error);
    }

    public void pauseTask(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.get(taskId);
        if (future != null) {
            future.cancel(false);
            scheduledTasks.remove(taskId);
            log.info("任务已暂停: taskId={}", taskId);
        }
    }

    public void resumeTask(ScheduledTask task) {
        scheduleTask(task);
    }
}
