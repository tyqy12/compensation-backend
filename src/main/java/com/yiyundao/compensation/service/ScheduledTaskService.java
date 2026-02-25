package com.yiyundao.compensation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yiyundao.compensation.dto.TaskScheduleDTO;
import com.yiyundao.compensation.entity.ScheduledTask;
import com.yiyundao.compensation.entity.ScheduledTaskExecution;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduledTaskService extends IService<ScheduledTask> {

    List<ScheduledTask> listTasksByStatus(ScheduledTask.TaskStatus status);

    Long createTask(TaskScheduleDTO dto);

    void updateTask(Long id, TaskScheduleDTO dto);

    void pauseTask(Long id);

    void resumeTask(Long id);

    void deleteTask(Long id);

    Long triggerTask(Long id);

    List<ScheduledTaskExecution> getExecutionLogs(Long taskId, int limit);

    void executeTask(ScheduledTask task);

    /**
     * 根据 Cron 表达式计算下次执行时间
     *
     * @param cronExpression Cron 表达式
     * @return 下次执行时间
     */
    LocalDateTime calculateNextExecutionTime(String cronExpression);
}
