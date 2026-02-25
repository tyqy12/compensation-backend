package com.yiyundao.compensation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        task.setNextExecuteTime(calculateNextExecutionTime(dto.getCronExpression()));

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

        task.setTaskName(dto.getTaskName());
        task.setTaskGroup(dto.getTaskGroup());
        task.setCronExpression(dto.getCronExpression());
        task.setDescription(dto.getDescription());
        task.setMaxRetryCount(dto.getMaxRetryCount() != null ? dto.getMaxRetryCount() : task.getMaxRetryCount());
        task.setRetryIntervalSeconds(dto.getRetryIntervalSeconds() != null ? dto.getRetryIntervalSeconds() : task.getRetryIntervalSeconds());
        task.setAlarmEnabled(dto.getAlarmEnabled());
        task.setAlarmReceivers(dto.getAlarmReceivers());
        task.setHandlerBean(dto.getHandlerBean());
        task.setNextExecuteTime(calculateNextExecutionTime(dto.getCronExpression()));

        updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pauseTask(Long id) {
        ScheduledTask task = getById(id);
        if (task != null) {
            task.setStatus(ScheduledTask.TaskStatus.PAUSED);
            updateById(task);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumeTask(Long id) {
        ScheduledTask task = getById(id);
        if (task != null) {
            task.setStatus(ScheduledTask.TaskStatus.RUNNING);
            task.setNextExecuteTime(calculateNextExecutionTime(task.getCronExpression()));
            updateById(task);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        // 执行任务
        executeTask(task);

        return execution.getId();
    }

    @Override
    public List<ScheduledTaskExecution> getExecutionLogs(Long taskId, int limit) {
        return executionMapper.selectList(new LambdaQueryWrapper<ScheduledTaskExecution>()
                .eq(ScheduledTaskExecution::getTaskId, taskId)
                .orderByDesc(ScheduledTaskExecution::getStartTime)
                .last("LIMIT " + limit));
    }

    @Override
    public void executeTask(ScheduledTask task) {
        log.info("开始执行任务: taskKey={}, handlerBean={}", task.getTaskKey(), task.getHandlerBean());
        long startTime = System.currentTimeMillis();
        TaskHandler.TaskExecutionResult result = null;

        try {
            // 根据 handlerBean 获取任务处理器
            TaskHandler handler = getTaskHandler(task.getHandlerBean());

            if (handler == null) {
                throw new RuntimeException("未找到任务处理器: handlerBean=" + task.getHandlerBean());
            }

            // 执行任务
            result = handler.execute(task.getTaskKey(), null);

            if (!result.success()) {
                throw new RuntimeException("任务执行失败: " + result.message());
            }

            // 更新任务状态
            task.setLastExecuteTime(LocalDateTime.now());
            task.setStatus(ScheduledTask.TaskStatus.SUCCESS);
            task.setLastResult("SUCCESS: " + result.message());
            task.setRetryCount(0);
            task.setNextExecuteTime(calculateNextExecutionTime(task.getCronExpression()));
            updateById(task);

            log.info("任务执行成功: taskKey={}, handler={}, duration={}ms, message={}",
                    task.getTaskKey(), task.getHandlerBean(), System.currentTimeMillis() - startTime, result.message());

        } catch (Exception e) {
            log.error("任务执行失败: taskKey={}, handlerBean={}", task.getTaskKey(), task.getHandlerBean(), e);

            task.setStatus(ScheduledTask.TaskStatus.FAILED);
            task.setLastResult("FAILED: " + e.getMessage());
            task.setRetryCount(task.getRetryCount() + 1);
            updateById(task);

            // 检查是否需要重试
            if (task.getRetryCount() < task.getMaxRetryCount()) {
                log.info("任务将在 {} 秒后重试: taskKey={}, retryCount={}/{}",
                        task.getRetryIntervalSeconds(), task.getTaskKey(), task.getRetryCount(), task.getMaxRetryCount());
            } else {
                log.error("任务重试次数已达上限: taskKey={}, maxRetryCount={}",
                        task.getTaskKey(), task.getMaxRetryCount());
            }

            throw new RuntimeException("任务执行失败: " + e.getMessage(), e);
        }
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
}
