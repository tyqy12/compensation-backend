package com.yiyundao.compensation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yiyundao.compensation.dto.TaskScheduleDTO;
import com.yiyundao.compensation.entity.ScheduledTask;
import com.yiyundao.compensation.entity.ScheduledTaskExecution;
import com.yiyundao.compensation.mapper.ScheduledTaskExecutionMapper;
import com.yiyundao.compensation.mapper.ScheduledTaskMapper;
import com.yiyundao.compensation.service.ScheduledTaskService;
import com.yiyundao.compensation.service.TaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskServiceImpl extends ServiceImpl<ScheduledTaskMapper, ScheduledTask>
        implements ScheduledTaskService {

    private static final int DEFAULT_LOG_LIMIT = 50;
    private static final int MAX_LOG_LIMIT = 200;

    private final ScheduledTaskExecutionMapper executionMapper;
    private final ApplicationContext applicationContext;

    /**
     * 任务处理器缓存（Bean Name -> Handler Instance）
     */
    private final Map<String, TaskHandler> handlerCache = new ConcurrentHashMap<>();

    @Override
    public List<ScheduledTask> listTasksByStatus(ScheduledTask.TaskStatus status) {
        return list(new LambdaQueryWrapper<ScheduledTask>()
                .eq(ScheduledTask::getStatus, status)
                .orderByDesc(ScheduledTask::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(TaskScheduleDTO dto) {
        LocalDateTime nextExecuteTime = requireNextExecutionTime(dto.getCronExpression());

        ScheduledTask task = new ScheduledTask();
        task.setTaskKey(dto.getTaskKey());
        task.setTaskName(dto.getTaskName());
        task.setTaskGroup(dto.getTaskGroup());
        task.setCronExpression(dto.getCronExpression());
        task.setDescription(dto.getDescription());
        task.setStatus(ScheduledTask.TaskStatus.PAUSED);
        task.setRetryCount(0);
        task.setMaxRetryCount(dto.getMaxRetryCount() != null ? dto.getMaxRetryCount() : 3);
        task.setRetryIntervalSeconds(dto.getRetryIntervalSeconds() != null ? dto.getRetryIntervalSeconds() : 60);
        task.setAlarmEnabled(dto.getAlarmEnabled());
        task.setAlarmReceivers(dto.getAlarmReceivers());
        task.setHandlerBean(dto.getHandlerBean());
        task.setNextExecuteTime(nextExecuteTime);

        save(task);
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(Long id, TaskScheduleDTO dto) {
        ScheduledTask task = getById(id);
        if (task == null) {
            throw new RuntimeException("任务不存在: " + id);
        }
        LocalDateTime nextExecuteTime = requireNextExecutionTime(dto.getCronExpression());

        task.setTaskName(dto.getTaskName());
        task.setTaskGroup(dto.getTaskGroup());
        task.setCronExpression(dto.getCronExpression());
        task.setDescription(dto.getDescription());
        task.setMaxRetryCount(dto.getMaxRetryCount() != null ? dto.getMaxRetryCount() : task.getMaxRetryCount());
        task.setRetryIntervalSeconds(dto.getRetryIntervalSeconds() != null ? dto.getRetryIntervalSeconds() : task.getRetryIntervalSeconds());
        task.setAlarmEnabled(dto.getAlarmEnabled());
        task.setAlarmReceivers(dto.getAlarmReceivers());
        task.setHandlerBean(dto.getHandlerBean());
        task.setNextExecuteTime(nextExecuteTime);

        updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pauseTask(Long id) {
        ScheduledTask task = getById(id);
        if (task == null) {
            throw new RuntimeException("任务不存在: " + id);
        }
        task.setStatus(ScheduledTask.TaskStatus.PAUSED);
        updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumeTask(Long id) {
        ScheduledTask task = getById(id);
        if (task == null) {
            throw new RuntimeException("任务不存在: " + id);
        }
        task.setStatus(ScheduledTask.TaskStatus.RUNNING);
        task.setNextExecuteTime(requireNextExecutionTime(task.getCronExpression()));
        updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        ScheduledTask task = getById(id);
        if (task == null) {
            throw new RuntimeException("任务不存在: " + id);
        }
        removeById(id);
    }

    @Override
    public Long triggerTask(Long id) {
        ScheduledTask task = getById(id);
        if (task == null) {
            throw new RuntimeException("任务不存在: " + id);
        }

        // 创建执行记录
        ScheduledTaskExecution execution = new ScheduledTaskExecution();
        execution.setTaskId(task.getId());
        execution.setTaskKey(task.getTaskKey());
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus(ScheduledTaskExecution.ExecutionStatus.RUNNING);
        executionMapper.insert(execution);

        try {
            // 执行任务
            executeTask(task);
            execution.setStatus(ScheduledTaskExecution.ExecutionStatus.SUCCESS);
            execution.setResult(task.getLastResult());
        } catch (RuntimeException ex) {
            execution.setStatus(ScheduledTaskExecution.ExecutionStatus.FAILED);
            execution.setErrorMessage(ex.getMessage());
            throw ex;
        } finally {
            LocalDateTime endTime = LocalDateTime.now();
            execution.setEndTime(endTime);
            execution.setDurationMs(java.time.Duration.between(execution.getStartTime(), endTime).toMillis());
            executionMapper.updateById(execution);
        }

        return execution.getId();
    }

    @Override
    public List<ScheduledTaskExecution> getExecutionLogs(Long taskId, int limit) {
        return executionMapper.selectList(new LambdaQueryWrapper<ScheduledTaskExecution>()
                .eq(ScheduledTaskExecution::getTaskId, taskId)
                .orderByDesc(ScheduledTaskExecution::getStartTime)
                .last("LIMIT " + safeLogLimit(limit)));
    }

    private int safeLogLimit(int limit) {
        if (limit < 1) {
            return DEFAULT_LOG_LIMIT;
        }
        return Math.min(limit, MAX_LOG_LIMIT);
    }

    @Override
    public void executeTask(ScheduledTask task) {
        ScheduledTask executionTask = resolveExecutionTask(task);
        log.info("开始执行任务: taskKey={}, handlerBean={}", executionTask.getTaskKey(), executionTask.getHandlerBean());
        long startTime = System.currentTimeMillis();
        TaskHandler.TaskExecutionResult result = null;
        ScheduledTask.TaskStatus previousStatus = executionTask.getStatus();

        try {
            // 根据 handlerBean 获取任务处理器
            TaskHandler handler = getTaskHandler(executionTask.getHandlerBean());

            if (handler == null) {
                throw new RuntimeException("未找到任务处理器: handlerBean=" + executionTask.getHandlerBean());
            }

            // 执行任务
            result = handler.execute(executionTask.getTaskKey(), null);

            if (!result.success()) {
                throw new RuntimeException("任务执行失败: " + result.message());
            }

            // 更新任务状态
            LocalDateTime now = LocalDateTime.now();
            executionTask.setLastExecuteTime(now);
            executionTask.setStatus(resolveSuccessStatus(previousStatus));
            executionTask.setLastResult("SUCCESS: " + result.message());
            executionTask.setRetryCount(0);
            executionTask.setNextExecuteTime(calculateNextExecutionTime(executionTask.getCronExpression()));
            updateExecutionOutcome(executionTask, previousStatus);
            copyExecutionOutcome(task, executionTask);

            log.info("任务执行成功: taskKey={}, handler={}, duration={}ms, message={}",
                    executionTask.getTaskKey(), executionTask.getHandlerBean(),
                    System.currentTimeMillis() - startTime, result.message());

        } catch (Exception e) {
            log.error("任务执行失败: taskKey={}, handlerBean={}", executionTask.getTaskKey(), executionTask.getHandlerBean(), e);

            int nextRetryCount = safeRetryCount(executionTask) + 1;
            int maxRetryCount = safeMaxRetryCount(executionTask);
            executionTask.setStatus(resolveFailureStatus(previousStatus, nextRetryCount, maxRetryCount));
            executionTask.setLastResult("FAILED: " + e.getMessage());
            executionTask.setRetryCount(nextRetryCount);
            executionTask.setNextExecuteTime(resolveFailureNextExecutionTime(executionTask, nextRetryCount, maxRetryCount));
            updateExecutionOutcome(executionTask, previousStatus);
            copyExecutionOutcome(task, executionTask);

            // 检查是否需要重试
            if (nextRetryCount < maxRetryCount) {
                log.info("任务将在 {} 秒后重试: taskKey={}, retryCount={}/{}",
                        safeRetryIntervalSeconds(executionTask), executionTask.getTaskKey(), nextRetryCount, maxRetryCount);
            } else {
                log.error("任务重试次数已达上限: taskKey={}, maxRetryCount={}",
                        executionTask.getTaskKey(), maxRetryCount);
            }

            throw new RuntimeException("任务执行失败: " + e.getMessage(), e);
        }
    }

    private ScheduledTask resolveExecutionTask(ScheduledTask task) {
        if (task == null || task.getId() == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        ScheduledTask latest = getById(task.getId());
        return latest != null ? latest : task;
    }

    private void updateExecutionOutcome(ScheduledTask task, ScheduledTask.TaskStatus previousStatus) {
        if (task == null || task.getId() == null || previousStatus == null || task.getStatus() == null) {
            return;
        }
        boolean updated = update(new UpdateWrapper<ScheduledTask>()
                .eq("id", task.getId())
                .eq("status", previousStatus.getCode())
                .eq("cron_expression", task.getCronExpression())
                .eq("handler_bean", task.getHandlerBean())
                .set("status", task.getStatus().getCode())
                .set("last_execute_time", task.getLastExecuteTime())
                .set("last_result", task.getLastResult())
                .set("retry_count", safeRetryCount(task))
                .set("next_execute_time", task.getNextExecuteTime()));
        if (!updated) {
            log.info("任务状态已变更，跳过执行结果回写: taskId={}, taskKey={}, previousStatus={}, finalStatus={}",
                    task.getId(), task.getTaskKey(), previousStatus, task.getStatus());
        }
    }

    private void copyExecutionOutcome(ScheduledTask target, ScheduledTask source) {
        if (target == null || source == null) {
            return;
        }
        target.setLastExecuteTime(source.getLastExecuteTime());
        target.setStatus(source.getStatus());
        target.setLastResult(source.getLastResult());
        target.setRetryCount(source.getRetryCount());
        target.setNextExecuteTime(source.getNextExecuteTime());
    }

    private ScheduledTask.TaskStatus resolveSuccessStatus(ScheduledTask.TaskStatus previousStatus) {
        if (previousStatus == ScheduledTask.TaskStatus.RUNNING || previousStatus == ScheduledTask.TaskStatus.PAUSED) {
            return previousStatus;
        }
        return ScheduledTask.TaskStatus.SUCCESS;
    }

    private ScheduledTask.TaskStatus resolveFailureStatus(ScheduledTask.TaskStatus previousStatus,
                                                         int retryCount,
                                                         int maxRetryCount) {
        if (previousStatus == ScheduledTask.TaskStatus.RUNNING && retryCount < maxRetryCount) {
            return ScheduledTask.TaskStatus.RUNNING;
        }
        if (previousStatus == ScheduledTask.TaskStatus.PAUSED) {
            return ScheduledTask.TaskStatus.PAUSED;
        }
        return ScheduledTask.TaskStatus.FAILED;
    }

    private LocalDateTime resolveFailureNextExecutionTime(ScheduledTask task, int retryCount, int maxRetryCount) {
        if (task.getStatus() == ScheduledTask.TaskStatus.RUNNING && retryCount < maxRetryCount) {
            return LocalDateTime.now().plusSeconds(safeRetryIntervalSeconds(task));
        }
        return null;
    }

    private int safeRetryCount(ScheduledTask task) {
        return task.getRetryCount() == null || task.getRetryCount() < 0 ? 0 : task.getRetryCount();
    }

    private int safeMaxRetryCount(ScheduledTask task) {
        return task.getMaxRetryCount() == null || task.getMaxRetryCount() < 1 ? 1 : task.getMaxRetryCount();
    }

    private int safeRetryIntervalSeconds(ScheduledTask task) {
        return task.getRetryIntervalSeconds() == null || task.getRetryIntervalSeconds() < 1
                ? 60
                : task.getRetryIntervalSeconds();
    }

    /**
     * 根据 handlerBean 获取任务处理器
     *
     * @param handlerBean handlerBean 名称
     * @return 任务处理器实例
     */
    private TaskHandler getTaskHandler(String handlerBean) {
        if (handlerBean == null || handlerBean.isBlank()) {
            return null;
        }

        // 先从缓存获取
        TaskHandler handler = handlerCache.get(handlerBean);
        if (handler != null) {
            return handler;
        }

        // 从 Spring 容器获取
        try {
            handler = applicationContext.getBean(handlerBean, TaskHandler.class);
            if (handler != null) {
                handlerCache.put(handlerBean, handler);
                log.debug("已加载任务处理器: handlerBean={}, taskType={}", handlerBean, handler.getTaskType());
            }
            return handler;
        } catch (Exception e) {
            log.warn("获取任务处理器失败: handlerBean={}", handlerBean, e);
            return null;
        }
    }

    @Override
    public LocalDateTime calculateNextExecutionTime(String cronExpression) {
        try {
            CronExpression cron = CronExpression.parse(cronExpression);
            return cron.next(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("解析 Cron 表达式失败: {}", cronExpression);
            return null;
        }
    }

    private LocalDateTime requireNextExecutionTime(String cronExpression) {
        LocalDateTime nextExecutionTime = calculateNextExecutionTime(cronExpression);
        if (nextExecutionTime == null) {
            throw new IllegalArgumentException("无效的 Cron 表达式");
        }
        return nextExecutionTime;
    }
}
