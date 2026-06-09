package com.yiyundao.compensation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

@Data
@TableName("scheduled_task")
public class ScheduledTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_key")
    private String taskKey;

    @TableField("task_name")
    private String taskName;

    @TableField("task_group")
    private String taskGroup;

    @TableField("cron_expression")
    private String cronExpression;

    @TableField("description")
    private String description;

    @TableField("status")
    private TaskStatus status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("max_retry_count")
    private Integer maxRetryCount;

    @TableField("retry_interval_seconds")
    private Integer retryIntervalSeconds;

    @TableField("last_execute_time")
    private LocalDateTime lastExecuteTime;

    @TableField("next_execute_time")
    private LocalDateTime nextExecuteTime;

    @TableField("last_result")
    private String lastResult;

    @TableField("alarm_enabled")
    private Boolean alarmEnabled;

    @TableField("alarm_receivers")
    private String alarmReceivers;

    @TableField("handler_bean")
    private String handlerBean;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    @Getter
    public enum TaskStatus {
        PAUSED(0),
        RUNNING(1),
        FAILED(2),
        SUCCESS(3);

        @EnumValue
        private final int code;

        TaskStatus(int code) {
            this.code = code;
        }
    }
}
